export default function LoadingSpinner({ label, size = "md", inline = false }) {
  const sz = size === "sm" ? 18 : size === "lg" ? 40 : 26;

  return (
    <div className={inline ? "fp-spinner-inline" : "fp-spinner-wrap"} role="status" aria-label={label || "Yükleniyor"}>
      <svg
        className="fp-spinner"
        width={sz}
        height={sz}
        viewBox="0 0 26 26"
        fill="none"
        aria-hidden="true"
      >
        <circle cx="13" cy="13" r="10" stroke="currentColor" strokeWidth="2.5" strokeOpacity="0.18" />
        <path
          d="M13 3 a10 10 0 0 1 10 10"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
      </svg>
      {label ? <span className="fp-spinner-label">{label}</span> : null}
    </div>
  );
}
