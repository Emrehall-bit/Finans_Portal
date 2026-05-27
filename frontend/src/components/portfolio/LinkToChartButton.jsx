import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";

/**
 * Portföy pozisyon kartından teknik analiz sayfasına geçiş butonu.
 * Stop-Loss çizim modu otomatik aktif olur.
 *
 * Props:
 *   position — { instrumentCode, stopLossPrice }
 */
export default function LinkToChartButton({ position }) {
  const navigate = useNavigate();
  const { t } = useTranslation();

  if (!position?.instrumentCode) return null;

  function handleClick() {
    navigate(
      `/analysis?symbol=${position.instrumentCode}&tool=STOPLOSS_LINE&preset=${position.stopLossPrice ?? ""}`,
    );
  }

  return (
    <button
      className="link-to-chart-btn"
      onClick={handleClick}
      title={t("portfolio.openInChart", "Grafikte Aç")}
    >
      📈
    </button>
  );
}
