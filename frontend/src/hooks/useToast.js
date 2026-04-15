import { useState } from "react";

export default function useToast() {
  const [toast, setToast] = useState(null);

  function showToast(type, message) {
    setToast({ type, message });
    setTimeout(() => setToast(null), 3000);
  }

  return { toast, showToast };
}
