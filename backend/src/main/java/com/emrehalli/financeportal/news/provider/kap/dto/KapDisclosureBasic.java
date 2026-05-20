package com.emrehalli.financeportal.news.provider.kap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KapDisclosureBasic {

    private String title;
    private String mkkMemberOid;
    private String companyTitle;
    private String stockCode;
    private Object relatedStocks;
    private String disclosureClass;
    private String disclosureType;
    private String disclosureCategory;
    private String publishDate;
    private String disclosureId;
    private Long disclosureIndex;
    private String summary;
    private Integer attachmentCount;
    private Integer year;
    private Integer donem;
    private String period;
    private String hasMultiLanguageSupport;
    private String fundType;
    private String senderType;
    private Boolean isLate;
    private String isChanged;
    private Boolean isBlocked;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMkkMemberOid() { return mkkMemberOid; }
    public void setMkkMemberOid(String mkkMemberOid) { this.mkkMemberOid = mkkMemberOid; }

    public String getCompanyTitle() { return companyTitle; }
    public void setCompanyTitle(String companyTitle) { this.companyTitle = companyTitle; }

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }

    public Object getRelatedStocks() { return relatedStocks; }
    public void setRelatedStocks(Object relatedStocks) { this.relatedStocks = relatedStocks; }

    public String getDisclosureClass() { return disclosureClass; }
    public void setDisclosureClass(String disclosureClass) { this.disclosureClass = disclosureClass; }

    public String getDisclosureType() { return disclosureType; }
    public void setDisclosureType(String disclosureType) { this.disclosureType = disclosureType; }

    public String getDisclosureCategory() { return disclosureCategory; }
    public void setDisclosureCategory(String disclosureCategory) { this.disclosureCategory = disclosureCategory; }

    public String getPublishDate() { return publishDate; }
    public void setPublishDate(String publishDate) { this.publishDate = publishDate; }

    public String getDisclosureId() { return disclosureId; }
    public void setDisclosureId(String disclosureId) { this.disclosureId = disclosureId; }

    public Long getDisclosureIndex() { return disclosureIndex; }
    public void setDisclosureIndex(Long disclosureIndex) { this.disclosureIndex = disclosureIndex; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getAttachmentCount() { return attachmentCount; }
    public void setAttachmentCount(Integer attachmentCount) { this.attachmentCount = attachmentCount; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getDonem() { return donem; }
    public void setDonem(Integer donem) { this.donem = donem; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getHasMultiLanguageSupport() { return hasMultiLanguageSupport; }
    public void setHasMultiLanguageSupport(String hasMultiLanguageSupport) { this.hasMultiLanguageSupport = hasMultiLanguageSupport; }

    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }

    public Boolean getIsLate() { return isLate; }
    public void setIsLate(Boolean isLate) { this.isLate = isLate; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getIsChanged() { return isChanged; }
    public void setIsChanged(String isChanged) { this.isChanged = isChanged; }

    public Boolean getIsBlocked() { return isBlocked; }
    public void setIsBlocked(Boolean isBlocked) { this.isBlocked = isBlocked; }
}
