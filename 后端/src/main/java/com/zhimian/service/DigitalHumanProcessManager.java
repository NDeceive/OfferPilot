package com.zhimian.service;

import com.zhimian.config.DigitalHumanProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 开发环境数字人进程管理器。
 *
 * <p>Spring Boot 就绪后检查数字人端口。若服务尚未运行，则调用数字人目录内的
 * 启动脚本；应用关闭时只清理本次应用拉起的进程树。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DigitalHumanProcessManager {

    private final DigitalHumanProperties properties;

    private volatile Process launchedProcess;
    private volatile boolean launchedByApplication;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void startIfNecessary() {
        DigitalHumanProperties.Launcher launcher = properties.getLauncher();
        if (!properties.isEnabled() || launcher == null || !launcher.isEnabled()) {
            log.info("数字人自动启动已关闭");
            return;
        }

        Endpoint endpoint = resolveEndpoint();
        if (isPortOpen(endpoint.host(), endpoint.port(), 500)) {
            log.info("数字人服务已在运行: {}", properties.getBaseUrl());
            return;
        }

        Path workdir = resolveWorkdir(launcher.getWorkdir());
        Path script = workdir.resolve(launcher.getScript()).normalize();
        if (!Files.isDirectory(workdir)) {
            log.warn("数字人目录不存在，跳过自动启动: {}", workdir);
            return;
        }
        if (!Files.isRegularFile(script)) {
            log.warn("数字人启动脚本不存在，跳过自动启动: {}", script);
            return;
        }

        try {
            Path logDirectory = Paths.get("logs").toAbsolutePath().normalize();
            Files.createDirectories(logDirectory);
            File processLog = logDirectory.resolve("digital-human.log").toFile();

            ProcessBuilder builder = new ProcessBuilder(buildCommand(script));
            builder.directory(workdir.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(processLog));

            launchedProcess = builder.start();
            launchedByApplication = true;
            log.info("正在启动数字人服务，PID={}，日志={}",
                    launchedProcess.pid(), processLog.getAbsolutePath());

            monitorStartup(endpoint, launcher.getStartupWaitMs());
        } catch (Exception exception) {
            launchedProcess = null;
            launchedByApplication = false;
            log.error("数字人服务自动启动失败，面试其他功能仍可继续使用", exception);
        }
    }

    private List<String> buildCommand(Path script) {
        if (isWindows()) {
            return List.of("cmd.exe", "/d", "/c", "call", script.toString());
        }
        return List.of("sh", script.toString());
    }

    private void monitorStartup(Endpoint endpoint, int startupWaitMs) {
        Thread monitor = new Thread(() -> {
            long deadline = System.nanoTime()
                    + Duration.ofMillis(Math.max(startupWaitMs, 1_000)).toNanos();
            while (System.nanoTime() < deadline) {
                if (isPortOpen(endpoint.host(), endpoint.port(), 500)) {
                    log.info("数字人服务启动成功: {}", properties.getBaseUrl());
                    return;
                }
                Process process = launchedProcess;
                if (process == null || !process.isAlive()) {
                    int exitCode = process == null ? -1 : process.exitValue();
                    log.error("数字人进程提前退出，exitCode={}，请检查 logs/digital-human.log", exitCode);
                    return;
                }
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            log.warn("等待数字人服务启动超时，请检查 logs/digital-human.log");
        }, "digital-human-startup-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    @PreDestroy
    public synchronized void stopLaunchedProcess() {
        DigitalHumanProperties.Launcher launcher = properties.getLauncher();
        if (!launchedByApplication || launchedProcess == null
                || launcher == null || !launcher.isStopOnShutdown()) {
            return;
        }

        Process process = launchedProcess;
        launchedProcess = null;
        launchedByApplication = false;

        try {
            process.descendants()
                    .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed())
                    .forEach(ProcessHandle::destroy);
            process.destroy();

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            log.info("已关闭由后端启动的数字人进程");
        } catch (Exception exception) {
            log.warn("关闭数字人进程时发生异常", exception);
        }
    }

    private Endpoint resolveEndpoint() {
        try {
            URI uri = URI.create(properties.getBaseUrl());
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return new Endpoint(uri.getHost() == null ? "127.0.0.1" : uri.getHost(), port);
        } catch (Exception exception) {
            log.warn("数字人 base-url 配置无效，使用 127.0.0.1:8010: {}", properties.getBaseUrl());
            return new Endpoint("127.0.0.1", 8010);
        }
    }

    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Path resolveWorkdir(String configuredWorkdir) {
        Path configured = Paths.get(configuredWorkdir);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path configuredPath = userDir.resolve(configured).normalize();
        if (Files.isDirectory(configuredPath)) {
            return configuredPath;
        }

        // 兼容从“后端”目录、OfferPilot 根目录或“项目”目录启动。
        List<Path> localCandidates = List.of(
                userDir.resolve("../数字人").normalize(),
                userDir.resolve("数字人").normalize(),
                userDir.resolve("../../数字人").normalize()
        );
        return localCandidates.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(configuredPath);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    private record Endpoint(String host, int port) {
    }
}
