import { formatDateTime, formatNumber } from "../../utils/formatters";

export default function WatchlistTable({ rows, onRemove }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Instrument</th>
            <th>Current Price</th>
            <th>Source</th>
            <th>Created</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((item) => (
            <tr key={item.id}>
              <td>{item.instrumentCode}</td>
              <td>{formatNumber(item.currentPrice)}</td>
              <td>{item.source || "-"}</td>
              <td>{formatDateTime(item.createdAt)}</td>
              <td>
                <button className="btn-danger" onClick={() => onRemove(item.id)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
