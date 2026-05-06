import { formatDateTime, formatNumber } from "../../utils/formatters";
import { useTranslation } from "react-i18next";

export default function AlertsTable({ rows, onCancel }) {
  const { t } = useTranslation();
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>{t("alerts.table.instrument")}</th>
            <th>{t("alerts.table.condition")}</th>
            <th>{t("alerts.table.threshold")}</th>
            <th>{t("alerts.table.status")}</th>
            <th>{t("alerts.table.lastPrice")}</th>
            <th>{t("watchlist.table.created")}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((item) => (
            <tr key={item.id}>
              <td>{item.instrumentCode || "-"}</td>
              <td>{item.conditionType || "-"}</td>
              <td>{formatNumber(item.targetPrice)}</td>
              <td>{item.status || "-"}</td>
              <td>{formatNumber(item.currentPrice)}</td>
              <td>{formatDateTime(item.createdAt)}</td>
              <td>
                {item.status === "ACTIVE" ? (
                  <button className="btn-danger" onClick={() => onCancel(item.id)}>
                    {t("common.cancel")}
                  </button>
                ) : (
                  "-"
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
