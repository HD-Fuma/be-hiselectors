package com.fuma.hiselectors.report;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬(local 프로파일)에서 앱 시작 시 Python 정성 엔진 워커(stt-worker/serve.py)를 함께 띄운다.
 * 매번 손으로 실행하지 않아도 되게 하는 개발 편의용. 앱 종료 시 함께 내린다.
 *
 * <p>이미 워커가 떠 있으면(포트 사용 중) 새로 띄우지 않는다. 배포 환경에서는 워커를 별도 서비스로
 * 운영하므로 이 런처는 local 에서만 동작한다. {@code analyzer.launch.enabled=false} 로 끌 수 있다.
 */
@Slf4j
@Profile("local")
@ConditionalOnProperty(name = "analyzer.launch.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class LocalAnalyzerLauncher implements ApplicationRunner {

    private final String pythonPath;
    private final String workerDir;
    private final int port;
    private Process process;

    public LocalAnalyzerLauncher(
            @org.springframework.beans.factory.annotation.Value(
                    "${analyzer.launch.python:stt-worker/.venv/Scripts/python.exe}") String pythonPath,
            @org.springframework.beans.factory.annotation.Value(
                    "${analyzer.launch.dir:stt-worker}") String workerDir,
            @org.springframework.beans.factory.annotation.Value(
                    "${analyzer.launch.port:8900}") int port) {
        this.pythonPath = pythonPath;
        this.workerDir = workerDir;
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isPortOpen()) {
            log.info("정성 엔진 워커가 이미 :{} 에서 실행 중 — 자동 실행 생략", port);
            return;
        }
        try {
            // inheritIO: 워커 로그(모델 로딩 등)를 앱 콘솔로 그대로 흘려보낸다.
            process = new ProcessBuilder(pythonPath, "serve.py")
                    .directory(new File(workerDir))
                    .inheritIO()
                    .start();
            log.info("정성 엔진 워커 자동 실행. python={}, dir={}, port={} (모델 로딩에 수십 초 소요)",
                    pythonPath, workerDir, port);
        } catch (IOException e) {
            // 워커 없이도 앱은 떠야 한다. 키워드·카테고리만 빠질 뿐.
            log.warn("정성 엔진 워커 자동 실행 실패. 수동 실행 필요(cd {} && python serve.py)", workerDir, e);
        }
    }

    private boolean isPortOpen() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @PreDestroy
    public void stop() {
        if (process != null && process.isAlive()) {
            log.info("정성 엔진 워커 종료");
            process.destroy();
        }
    }
}
