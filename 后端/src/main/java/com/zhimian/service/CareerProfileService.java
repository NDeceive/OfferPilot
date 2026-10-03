package com.zhimian.service;

import com.zhimian.common.BizException;
import com.zhimian.config.UserContext;
import com.zhimian.dto.CareerProfileRequest;
import com.zhimian.mapper.JobPositionMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CareerProfileService {
    private final JdbcTemplate jdbc;
    private final JobPositionMapper jobs;

    @PostConstruct
    void initializeSchema() {
        new ResourceDatabasePopulator(new ClassPathResource("db/profile.sql")).execute(jdbc.getDataSource());
    }

    public CareerProfileRequest get() {
        var rows = jdbc.query("SELECT stage,school,major,graduation_year,target_job_id,target_company FROM user_career_profile WHERE user_id=?",
                (rs, n) -> {
                    var profile = new CareerProfileRequest();
                    profile.setStage(rs.getString(1));
                    profile.setSchool(rs.getString(2));
                    profile.setMajor(rs.getString(3));
                    profile.setGraduationYear(rs.getString(4));
                    profile.setTargetJobId(rs.getObject(5, Long.class));
                    profile.setTargetCompany(rs.getString(6));
                    return profile;
                }, UserContext.getUserId());
        return rows.isEmpty() ? new CareerProfileRequest() : rows.get(0);
    }

    public CareerProfileRequest save(CareerProfileRequest profile) {
        if (profile.getTargetJobId() != null) {
            var job = jobs.selectById(profile.getTargetJobId());
            if (job == null || !Integer.valueOf(1).equals(job.getStatus())) throw new BizException("请选择可用的目标岗位");
        }
        jdbc.update("INSERT INTO user_career_profile (user_id,stage,school,major,graduation_year,target_job_id,target_company) VALUES (?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE stage=VALUES(stage),school=VALUES(school),major=VALUES(major),graduation_year=VALUES(graduation_year),target_job_id=VALUES(target_job_id),target_company=VALUES(target_company)",
                UserContext.getUserId(), text(profile.getStage()), text(profile.getSchool()), text(profile.getMajor()), text(profile.getGraduationYear()), profile.getTargetJobId(), text(profile.getTargetCompany()));
        return get();
    }

    public String avatar() {
        var rows = jdbc.query("SELECT avatar_data FROM user_career_profile WHERE user_id=?", (rs, n) -> rs.getBytes(1), UserContext.getUserId());
        return rows.isEmpty() || rows.get(0) == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(rows.get(0));
    }

    public String uploadAvatar(MultipartFile file) {
        byte[] normalized = normalizeAvatar(file);
        jdbc.update("INSERT INTO user_career_profile (user_id,avatar_data) VALUES (?,?) ON DUPLICATE KEY UPDATE avatar_data=VALUES(avatar_data)", UserContext.getUserId(), normalized);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(normalized);
    }

    static byte[] normalizeAvatar(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new BizException("头像文件须小于 5 MB");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BizException("请选择有效的 JPG 或 PNG 图片");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw new BizException("仅支持 JPG 和 PNG 图片");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width > 4096 || height > 4096 || width < 1 || height < 1) throw new BizException("图片尺寸不能超过 4096 像素");
                BufferedImage source = reader.read(0);
                BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, 256, 256);
                int side = Math.min(width, height), x = (width - side) / 2, y = (height - side) / 2;
                graphics.drawImage(source, 0, 0, 256, 256, x, y, x + side, y + side, null);
                graphics.dispose();
                var out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException e) { throw new BizException("图片读取失败，请更换文件"); }
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
