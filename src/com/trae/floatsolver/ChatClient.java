package com.trae.floatsolver;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * OpenAI 兼容接口的流式调用，只用平台自带的 HttpURLConnection + org.json。
 */
public class ChatClient {

    public interface Sink {
        void onDelta(String text);

        void onDone();

        void onError(String message);
    }

    private static final String SYSTEM = "你是一位严谨、耐心的中文做题辅导老师。"
            + "用户发来的是手机屏幕截图，请找出其中正在被作答或提出的题目并直接解答。"
            + "要求："
            + "1) 先逐字确认题干中的数字、符号、上下标、单位和选项，屏幕字迹不清时按最可能的读法说明；"
            + "2) 用一句话点明考点；"
            + "3) 分步推导，每一步都写出依据，不要跳步；"
            + "4) 算出结果后回代或反向验算一遍，确认无误再给结论；"
            + "5) 最后单独一行以「答案：」开头给出确定结论；选项题必须给出字母和对应原文；"
            + "6) 如果屏幕里有多道题，只讲最主要的那道；如果没有题目，就说明你看到的界面并询问需要帮什么；"
            + "7) 数学式一律用纯文本表达：分数写成 2/5，极限写成 lim(x→0)，根号写成 √x，乘号用 ×；"
            + "严禁使用 LaTeX 或 $、$$、\\frac、\\cdot、\\lim 之类的标记；"
            + "8) 语言简洁，不要寒暄，不要重复题目。";

    /** 精简模式：只给答案字母和一句理由，方便照着点 */
    private static final String BRIEF = "你在帮人快速看答案。用户发来的是手机屏幕截图，请找出其中正在作答的题目。"
            + "先仔细核对题干中的数字和选项，在心里完整算一遍并验算，再输出。"
            + "只输出三行："
            + "第一行固定格式「答案：X」，X 是正确选项字母，多选写成如 ABD，没有选项就写结论要点；"
            + "第二行「理由：」加一句话，不超过 40 字；"
            + "第三行「选项：」加正确选项的完整原文，如「选项：B. ①②④」，没有选项就省略这行。"
            + "绝对不要展开解题过程，不要寒暄，不要重复题目。";

    public static void ask(final String baseUrl, final String model, final String apiKey,
                           final String imageDataUrl, final boolean brief, final Sink sink) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String url = baseUrl.endsWith("/chat/completions")
                            ? baseUrl : baseUrl + "/chat/completions";
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(150000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);

                    JSONArray content = new JSONArray();
                    content.put(new JSONObject().put("type", "text")
                            .put("text", "请解答屏幕截图中的题目。"));
                    content.put(new JSONObject().put("type", "image_url")
                            .put("image_url", new JSONObject().put("url", imageDataUrl)));

                    JSONArray messages = new JSONArray();
                    messages.put(new JSONObject().put("role", "system").put("content", brief ? BRIEF : SYSTEM));
                    messages.put(new JSONObject().put("role", "user").put("content", content));

                    JSONObject body = new JSONObject()
                            .put("model", model)
                            .put("messages", messages)
                            .put("stream", true)
                            .put("temperature", 0.0);

                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    if (code < 200 || code >= 300) {
                        StringBuilder sb = new StringBuilder();
                        try {
                            BufferedReader er = new BufferedReader(new InputStreamReader(
                                    conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(),
                                    StandardCharsets.UTF_8));
                            String l;
                            while ((l = er.readLine()) != null && sb.length() < 300) sb.append(l);
                            er.close();
                        } catch (Throwable ignore) {
                        }
                        final String msg = "请求失败 HTTP " + code + " " + sb;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                sink.onError(msg);
                            }
                        });
                        return;
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader(
                            conn.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if (data.isEmpty() || data.equals("[DONE]")) continue;
                        try {
                            JSONObject j = new JSONObject(data);
                            JSONArray choices = j.optJSONArray("choices");
                            if (choices == null || choices.length() == 0) continue;
                            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                            if (delta == null) continue;
                            String t = delta.optString("content", "");
                            if (!t.isEmpty()) {
                                final String piece = t;
                                main.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        sink.onDelta(piece);
                                    }
                                });
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                    br.close();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            sink.onDone();
                        }
                    });
                } catch (final Throwable e) {
                    final String msg = (e instanceof java.net.UnknownHostException
                            ? "网络不可用，请检查网络" : e.getClass().getSimpleName() + ": " + e.getMessage());
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            sink.onError(msg);
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
}
