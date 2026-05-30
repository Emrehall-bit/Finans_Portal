import { useEffect, useRef, useState } from "react";

export default function useToast() {
  const [toast, setToast] = useState(null);
  const timerRef = useRef(null);

  function showToast(type, message) {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
    setToast({ type, message, id: Date.now() });
    timerRef.current = setTimeout(() => setToast(null), 2000);
  }

  useEffect(() => () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
  }, []);

  return { toast, showToast };
}
