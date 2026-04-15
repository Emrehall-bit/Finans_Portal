export default function ErrorMessage({ message }) {
  return <div className="status-box error">{message || "Something went wrong."}</div>;
}
