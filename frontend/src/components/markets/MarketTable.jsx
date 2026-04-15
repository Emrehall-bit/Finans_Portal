import { formatDateTime, formatNumber } from "../../utils/formatters";

export default function MarketTable({ rows = [] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Name</th>
            <th>Price</th>
            <th>Source</th>
            <th>Last Updated</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((item, index) => (
            <tr key={`${item.symbol || "item"}-${index}`}>
              <td>{item.symbol || "-"}</td>
              <td>{item.name || "-"}</td>
              <td>{formatNumber(item.price)}</td>
              <td>{item.source || "-"}</td>
              <td>{formatDateTime(item.lastUpdated)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
