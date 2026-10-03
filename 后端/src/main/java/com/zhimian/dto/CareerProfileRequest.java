package com.zhimian.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CareerProfileRequest {
    @Pattern(regexp = "|实习|校招|社招")
    private String stage = "";
    @Size(max = 100)
    private String school = "";
    @Size(max = 100)
    private String major = "";
    @Pattern(regexp = "|(?:19|20|21)[0-9]{2}")
    private String graduationYear = "";
    private Long targetJobId;
    @Size(max = 100)
    private String targetCompany = "";
}
