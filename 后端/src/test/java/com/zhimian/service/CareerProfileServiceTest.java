package com.zhimian.service;

import com.zhimian.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class CareerProfileServiceTest {
    @Test void rejectsPretendImagesAndOversizedFiles() {
        assertThrows(BizException.class, () -> CareerProfileService.normalizeAvatar(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
        assertThrows(BizException.class, () -> CareerProfileService.normalizeAvatar(new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1])));
    }
    @Test void normalizesRealImageAndRejectsExcessiveDimensions() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] result = CareerProfileService.normalizeAvatar(new MockMultipartFile("file", "photo.png", "image/png", out.toByteArray()));
        var decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(256, decoded.getWidth());
        assertEquals(256, decoded.getHeight());
        out.reset();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", out);
        assertThrows(BizException.class, () -> CareerProfileService.normalizeAvatar(new MockMultipartFile("file", out.toByteArray())));
    }
}
