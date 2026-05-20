package com.emrehalli.financeportal.news.provider.kap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KapDisclosureBasic {

    private String title;
    private String stockCode;
    private String companyTitle;
    private String disclosureClass;
    private String disclosureType;
    private String disclosureCategory;
    private String publishDate;
    private Long disclosureId;
    private Long disclosureIndex;
    private String summary;
    private Integer attachmentCount;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }

    public String getCompanyTitle() { return companyTitle; }
    public void setCompanyTitle(String companyTitle) { this.companyTitle = companyTitle; }

    public String getDisclosureClass() { return disclosureClass; }
    public void setDisclosureClass(String disclosureClass) { this.disclosureClass = disclosureClass; }

    public String getDisclosureType() { return disclosureType; }
    public void setDisclosureType(String disclosureType) { this.disclosureType = disclosureType; }

    public String getDisclosureCategory() { return disclosureCategory; }
    public void setDisclosureCategory(String disclosureCategory) { this.disclosureCategory = disclosureCategory; }

    public String getPublishDate() { return publishDate; }
    public void setPublishDate(String publishDate) { this.publishDate = publishDate; }

    public Long getDisclosureId() { return disclosureId; }
    public void setDisclosureId(Long disclosureId) { this.disclosureId = disclosureId; }

    public Long getDisclosureIndex() { return disclosureIndex; }
    public void setDisclosureIndex(Long disclosureIndex) { this.disclosureIndex = disclosureIndex; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getAttachmentCount() { return attachmentCount; }
    public void setAttachmentCount(Integer attachmentCount) { this.attachmentCount = attachmentCount; }
}
