import { formatDateTime, formatNumber, formatPercent } from "../../utils/formatters";

export default function MarketTable({ rows = [] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Name</th>
            <th>Type</th>
            <th>Price</th>
            <th>Change</th>
            <th>Currency</th>
            <th>Source</th>
            <th>Price Time</th>
            <th>Fetched At</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((item, index) => (
            <tr key={`${item.symbol || "item"}-${index}`}>
              <td>{item.symbol || "-"}</td>
              <td>{item.name || "-"}</td>
              <td>{item.instrumentType || "-"}</td>
              <td>{formatNumber(item.price)}</td>
              <td>
                {[formatNumber(item.changeAmount), formatPercent(item.changePercent)]
                  .filter((value) => value !== "-")
                  .join(" / ") || "-"}
              </td>
              <td>{item.currency || "-"}</td>
              <td>{item.source || "-"}</td>
              <td>{formatDateTime(item.priceTime)}</td>
              <td>{formatDateTime(item.fetchedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
