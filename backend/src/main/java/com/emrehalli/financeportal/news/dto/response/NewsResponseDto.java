package com.emrehalli.financeportal.news.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Haber yanıt modeli")
public class NewsResponseDto {

    @Schema(description = "Haber ID", example = "1")
    private Long id;

    @Schema(description = "Harici kaynak ID'si", example = "abc-123")
    private String externalId;

    @Schema(description = "Haber başlığı", example = "TCMB faiz kararını açıkladı")
    private String title;

    @Schema(description = "Haber özeti")
    private String summary;

    @Schema(description = "İçerik ön izlemesi")
    private String contentPreview;

    @Schema(description = "Kaynak", example = "Bloomberg")
    private String source;

    @Schema(description = "Kaynak adı", example = "Bloomberg HT")
    private String sourceName;

    @Schema(description = "Sağlayıcı", example = "BLOOMBERG")
    private String provider;

    @Schema(description = "Dil", example = "tr")
    private String language;

    @Schema(description = "Bölge kapsamı", example = "NATIONAL")
    private String regionScope;

    @Schema(description = "Kategori", example = "ECONOMY")
    private String category;

    @Schema(description = "İlişkili sembol", example = "THYAO")
    private String relatedSymbol;

    @Schema(description = "Haber URL'si")
    private String url;

    @Schema(description = "Kaynak URL'si")
    private String sourceUrl;

    @Schema(description = "Görsel URL'si")
    private String imageUrl;

    @Schema(description = "Yayınlanma tarihi")
    private LocalDateTime publishedAt;

    @Schema(description = "Önem puanı (0-100)", example = "85")
    private Integer importanceScore;

    @Schema(description = "İçerik kalite durumu", example = "VERIFIED")
    private String qualityStatus;

    @Schema(description = "KAP bildirimi mi", example = "false")
    private Boolean isKapDisclosure;

    @Schema(description = "Bildirim tipi", example = "MATERIAL_EVENT")
    private String disclosureType;
}

