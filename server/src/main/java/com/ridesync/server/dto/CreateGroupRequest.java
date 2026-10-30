package com.ridesync.server.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGroupRequest {
    @NotBlank(message = "그룹 이름을 입력해주세요.")
    @Size(max = 100, message = "그룹 이름은 100자 이하여야 합니다.")
    private String groupName;

    @Size(max = 500, message = "그룹 설명은 500자 이하여야 합니다.")
    private String description;

    @NotNull(message = "최대 인원을 입력해주세요.")
    @Min(value = 2, message = "최대 인원은 2명 이상이어야 합니다.")
    @Max(value = 50, message = "최대 인원은 50명 이하여야 합니다.")
    private Integer maxMembers;
}
