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
            + "用户发来的是手机上正在作答的题目内容（可能是截图，也可能是界面文字）。"
            + "请找出其中正在被作答或提出的题目并直接解答。"
            + "要求："
            + "1) 先逐字确认题干和全部选项的文字，屏幕字迹不清时按最可能的读法说明；"
            + "2) 选择题必须逐项分析：把 A、B、C、D 每一项单独列出来，写明它对或错的原因，再下结论；"
            + "3) 文科题目（历史、政治、语文、地理）注意时间、人物、事件的对应关系，"
            + "不要把相近概念、相近年代或相似说法混在一起；拿不准的地方要说明依据或存在争议；"
            + "4) 理科题目分步推导，每步写依据，不跳步，算完回代验算；"
            + "5) 最后单独一行以「答案：」开头给出确定结论；选项题必须给出字母和该选项原文；"
            + "6) 如果屏幕里有多道题，只讲最主要的那道；如果没有题目，就说明你看到的界面并询问需要帮什么；"
            + "7) 数学式用纯文本表达：分数写成 2/5，根号写成 √x，乘号用 ×，"
            + "严禁使用 LaTeX 或 $、$$、\\frac 之类的标记；"
            + "8) 如果你对结论把握不大（题干残缺、字迹不清、选项对不上），"
            + "就在「答案：」那一行末尾加上「（存疑）」；有把握时绝对不要加；"
            + "9) 语言简洁，不要寒暄，不要重复题目。";

    /** 精简模式：只给答案字母和一句理由，方便照着点 */
    private static final String BRIEF = "你在帮人快速看答案。用户发来的是手机上的题目内容（截图或界面文字），"
            + "请找出其中正在作答的题目。"
            + "选择题先在心里逐项排除错误选项，再核对体裁与年代、概念是否对应，确认后输出。"
            + "只输出三行："
            + "第一行固定格式「答案：X」，X 是正确选项字母，多选写成如 ABD，没有选项就写结论要点；"
            + "第二行「理由：」加一句话，不超过 40 字；"
            + "第三行「选项：」加正确选项的完整原文，如「选项：B. ①②④」，没有选项就省略这行。"
            + "全文只保留一个「答案：」行，中间推演出现过别的候选也不要写在答案行里。"
            + "把握不大时在「答案：」那行末尾加「（存疑）」。"
            + "绝对不要展开解题过程，不要寒暄，不要重复题目。";

    /** 只要答案：一行结论，不解释 */
    private static final String ONLY = "只回答答案，不要任何解释、过程、寒暄。"
            + "先判断题干是单选还是多选（题面出现「多选」「不定项」「至少」「正确的有」等提示即为多选），"
            + "多选题必须给出全部正确选项的字母，按字母顺序连写，一个都不能漏，"
            + "例如「答案：ABD. ①③④」；单选题只给一个字母。"
            + "输出格式固定一行：选择题写「答案：字母. 选项原文」，填空或问答写「答案：结论」。"
            + "全文只能出现一个「答案：」，绝对不允许给两个答案或两个候选；"
            + "多选题把全部字母连写在一行里。"
            + "题干看不清或有多道题时，只答最主要的那道，答不出就写「答案：看不清」。"
            + "把握不大时在答案末尾加「（存疑）」。";

    public static void ask(final String baseUrl, final String model, final String apiKey,
                           final String imageDataUrl, final boolean brief, final boolean answerOnly,
                           final Sink sink) {
        try {
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "text")
                    .put("text", "请解答屏幕截图中的题目。"));
            content.put(new JSONObject().put("type", "image_url")
                    .put("image_url", new JSONObject().put("url", imageDataUrl)));

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system")
                    .put("content", answerOnly ? ONLY : (brief ? BRIEF : SYSTEM)));
            messages.put(new JSONObject().put("role", "user").put("content", content));
            post(baseUrl, model, apiKey, messages, sink);
        } catch (Throwable e) {
            sink.onError(String.valueOf(e.getMessage()));
        }
    }

    /** 追问：带着同一道题的素材（截图或屏幕文字）和上一轮解答继续问 */
    public static void followUp(final String baseUrl, final String model, final String apiKey,
                                final String imageDataUrl, final String screenText,
                                final String previousAnswer,
                                final String question, final Sink sink) {
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", SYSTEM));

            if (imageDataUrl != null) {
                JSONArray content = new JSONArray();
                content.put(new JSONObject().put("type", "text").put("text", "这是题目的截图。"));
                content.put(new JSONObject().put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", imageDataUrl)));
                messages.put(new JSONObject().put("role", "user").put("content", content));
            } else {
                messages.put(new JSONObject().put("role", "user").put("content",
                        "这是当前手机屏幕上显示的全部文字（按界面从上到下排列），请从中找出题目：\n\n"
                                + (screenText == null ? "（无）" : screenText)));
            }

            messages.put(new JSONObject().put("role", "assistant")
                    .put("content", Tex.plain(previousAnswer)));
            messages.put(new JSONObject().put("role", "user").put("content", question));
            post(baseUrl, model, apiKey, messages, sink);
        } catch (Throwable e) {
            sink.onError(String.valueOf(e.getMessage()));
        }
    }

    private static void post(final String baseUrl, final String model, final String apiKey,
                             final JSONArray messages, final Sink sink) {
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
