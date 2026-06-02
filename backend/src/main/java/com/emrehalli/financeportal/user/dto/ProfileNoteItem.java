package com.emrehalli.financeportal.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileNoteItem {

    private String id;

    @NotBlank(message = "Note content cannot be blank")
    @Size(max = 1000, message = "Note content must be 1000 characters or fewer")
    private String content;

    private String createdAt;

    private String updatedAt;
}
