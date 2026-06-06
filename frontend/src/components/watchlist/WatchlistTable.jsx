import { useTranslation } from "react-i18next";
import { formatDateTime, formatInstrumentValue } from "../../utils/formatters";
import { formatInstrumentCode } from "../../utils/instrumentUtils";

export default function WatchlistTable({ rows, onRemove }) {
  const { t } = useTranslation();

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>{t("watchlist.table.instrument")}</th>
            <th>{t("watchlist.table.currentPrice")}</th>
            <th>{t("watchlist.table.source")}</th>
            <th>{t("watchlist.table.created")}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((item) => (
            <tr key={item.id}>
              <td>{formatInstrumentCode(item.instrumentCode)}</td>
              <td>{formatInstrumentValue(item.currentPrice, item)}</td>
              <td>{item.source || "-"}</td>
              <td>{formatDateTime(item.createdAt)}</td>
              <td>
                <button className="btn-danger" onClick={() => onRemove(item.id)}>
                  {t("watchlist.remove")}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
