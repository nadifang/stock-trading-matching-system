import { useState } from "react";

interface HeaderLogoProps {
  src?: string;
  alt?: string;
}

function LogoFallback() {
  return (
    <div className="h-12 px-4 rounded-xl bg-gradient-to-r from-orange-500 to-amber-500 text-white flex items-center font-bold tracking-wide">
      STMS Trade
    </div>
  );
}

export default function HeaderLogo({ src = "/logo.png", alt = "STMS Logo" }: HeaderLogoProps) {
  const [loadError, setLoadError] = useState(false);

  if (loadError) {
    return <LogoFallback />;
  }

  return (
    <img
      src={src}
      alt={alt}
      className="h-12 w-auto object-contain"
      onError={() => setLoadError(true)}
      loading="eager"
      decoding="async"
    />
  );
}
