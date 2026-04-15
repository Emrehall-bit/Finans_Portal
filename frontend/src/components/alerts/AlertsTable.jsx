import { formatDateTime, formatNumber } from "../../utils/formatters";

export default function AlertsTable({ rows, onCancel }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Instrument</th>
            <th>Condition</th>
            <th>Target</th>
            <th>Status</th>
            <th>Current Price</th>
            <th>Created</th>
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
                    Cancel
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
