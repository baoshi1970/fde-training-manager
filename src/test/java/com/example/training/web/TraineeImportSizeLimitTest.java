package com.example.training.web;

import com.example.training.repository.TraineeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F10 CSV 导入学员：上传大小上限（1MB）的测试。
 * MockMvc 不检查上传大小，所以真正启动服务器，像浏览器一样登录后再上传。
 * 使用单独的 H2 库：避免这个上下文启动时（create-drop）重建其他测试共用的表。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:size-limit-test;DB_CLOSE_DELAY=-1")
class TraineeImportSizeLimitTest {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    @Autowired
    TestRestTemplate rest;
    @Autowired
    TraineeRepository traineeRepository;

    /** 登录后的 Cookie（名字 → 值） */
    private final Map<String, String> cookies = new LinkedHashMap<>();

    @Test
    void 超过1MB的文件_被服务器拒绝且一条也不登记() {
        long before = traineeRepository.count();
        login();
        String csrf = csrfOf(get("/trainees/import").getBody());

        byte[] csv = bigCsv();
        assertThat(csv.length).as("测试数据要超过 1MB").isGreaterThan(1024 * 1024);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("_csrf", csrf);
        body.add("file", new ByteArrayResource(csv) {
            @Override
            public String getFilename() {
                return "big.csv";
            }
        });
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.TEXT_HTML));   // 和浏览器一样要 HTML
        ResponseEntity<String> res = rest.exchange("/trainees/import", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        // 实测结果（2026-11-18）：
        // 1. multipart 超过上限，CsrfFilter 读不到 _csrf → 403
        // 2. 转到共通错误页 /error 时又解析了一次 multipart → MaxUploadSizeExceededException → 状态码变成 413
        // 3. 错误页没能渲染，响应体为空（浏览器显示它自己的错误画面）。不进 Controller，不显示「超过 500 行」
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(res.getBody()).as("响应体为空；如果以后做了专门的提示，请修改这个测试").isNull();
        assertThat(traineeRepository.count()).isEqualTo(before);
    }

    /** 表头＋合格数据，大小约 1.2MB（行数远超 500 行，但请求到不了 Service） */
    private static byte[] bigCsv() {
        StringBuilder sb = new StringBuilder("name,email,affiliation,joinedOn\n");
        for (int i = 1; sb.length() < 1_200_000; i++) {
            sb.append("学员 ").append(i).append(",user").append(i)
                    .append("@example.com,X 社 开发部,2026-11-01\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---- 像浏览器一样登录：登录页取 CSRF token，提交账号密码，保存 Cookie ----

    private void login() {
        String csrf = csrfOf(get("/login").getBody());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "admin");
        form.add("password", "admin123");
        form.add("_csrf", csrf);
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> res = rest.exchange("/login", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
        saveCookies(res);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(res.getHeaders().getLocation()).as("登录失败").hasToString("/");
    }

    private ResponseEntity<String> get(String path) {
        ResponseEntity<String> res = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers()), String.class);
        saveCookies(res);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res;
    }

    private static String csrfOf(String html) {
        Matcher m = CSRF.matcher(html);
        assertThat(m.find()).as("画面里没有 CSRF token").isTrue();
        return m.group(1);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        if (!cookies.isEmpty()) {
            headers.add(HttpHeaders.COOKIE, String.join("; ",
                    cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
        }
        return headers;
    }

    private void saveCookies(ResponseEntity<?> res) {
        List<String> setCookies = res.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        for (String c : setCookies) {
            String pair = c.split(";", 2)[0];
            int eq = pair.indexOf('=');
            cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
    }
}
