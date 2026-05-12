package com.emrehalli.financeportal.market.provider.fund.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TefasFundListRequest {

    private String dil = "TR";
    private String fonTipi = "YAT";
    private String kurucuKodu;
    private String sfonTurKod;
    private String fonTurAciklama;
    private Integer islem = 1;
    private String basTarih;
    private String bitTarih;
    private Integer calismaTipi = 2;
    private String donemGetiri1a = "1";
    private String donemGetiri1y = "1";
    private String donemGetiri3a = "1";
    private String donemGetiri3y = "1";
    private String donemGetiri5y = "1";
    private String donemGetiri6a = "1";
    private String donemGetiriyb = "1";
    private String fonGrubu;
    private String fonTurKod;
    private String getiriOrani = "1";
}
