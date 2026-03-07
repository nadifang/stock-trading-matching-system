import { useEffect, useMemo, useState } from "react";

type BannerItem = {
  id: string;
  imageUrl: string;
  title: string;
  subtitle?: string;
};

const defaultBanners: BannerItem[] = [
  {
    id: "new-listing",
    imageUrl: "https://placehold.co/1400x560/f97316/ffffff?text=NEW+LISTING",
    title: "New Listing Zone",
    subtitle: "Featured symbols with live tracking",
  },
  {
    id: "fee-cut",
    imageUrl: "https://placehold.co/1400x560/0ea5e9/ffffff?text=LOW+FEE+TRADING",
    title: "Limited Fee Offer",
    subtitle: "Lower order fee during campaign window",
  },
  {
    id: "risk-alert",
    imageUrl: "https://placehold.co/1400x560/14b8a6/ffffff?text=RISK+CONTROL+UPGRADE",
    title: "Risk Control Upgrade",
    subtitle: "Faster abnormal-order interception",
  },
];

interface BannerCarouselProps {
  banners?: BannerItem[];
  intervalMs?: number;
}

export default function BannerCarousel({
  banners = defaultBanners,
  intervalMs = 3500,
}: BannerCarouselProps) {
  const safeBanners = useMemo(() => (banners.length > 0 ? banners : defaultBanners), [banners]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [failedBannerIds, setFailedBannerIds] = useState<string[]>([]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setActiveIndex((prev) => (prev + 1) % safeBanners.length);
    }, intervalMs);

    return () => window.clearInterval(timer);
  }, [intervalMs, safeBanners.length]);

  const goPrev = () => {
    setActiveIndex((prev) => (prev - 1 + safeBanners.length) % safeBanners.length);
  };

  const goNext = () => {
    setActiveIndex((prev) => (prev + 1) % safeBanners.length);
  };

  return (
    <section className="relative w-full h-full overflow-hidden rounded-2xl shadow-lg bg-slate-200">
      {safeBanners.map((banner, index) => (
        <div
          key={banner.id}
          className={`absolute inset-0 transition-opacity duration-700 ${
            index === activeIndex ? "opacity-100" : "opacity-0"
          }`}
          aria-hidden={index !== activeIndex}
        >
          {failedBannerIds.includes(banner.id) ? (
            <div className="w-full h-full bg-gradient-to-r from-indigo-500 via-blue-500 to-cyan-500" />
          ) : (
            <img
              src={banner.imageUrl}
              alt={banner.title}
              className="w-full h-full object-cover"
              draggable={false}
              onError={() =>
                setFailedBannerIds((prev) =>
                  prev.includes(banner.id) ? prev : [...prev, banner.id]
                )
              }
            />
          )}
          <div className="absolute inset-0 bg-gradient-to-r from-black/60 via-black/35 to-transparent" />
          <div className="absolute left-6 bottom-8 text-white">
            <p className="text-2xl md:text-3xl font-bold tracking-wide">{banner.title}</p>
            {banner.subtitle && <p className="mt-2 text-sm md:text-base text-white/90">{banner.subtitle}</p>}
          </div>
        </div>
      ))}

      <button
        type="button"
        aria-label="Previous banner"
        onClick={goPrev}
        className="absolute left-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-black/35 text-white text-xl hover:bg-black/50"
      >
        {"<"}
      </button>
      <button
        type="button"
        aria-label="Next banner"
        onClick={goNext}
        className="absolute right-4 top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-black/35 text-white text-xl hover:bg-black/50"
      >
        {">"}
      </button>

      <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
        {safeBanners.map((banner, index) => (
          <button
            key={banner.id}
            type="button"
            aria-label={`Go to banner ${index + 1}`}
            onClick={() => setActiveIndex(index)}
            className={`w-2.5 h-2.5 rounded-full transition-all ${
              index === activeIndex ? "bg-white scale-110" : "bg-white/60"
            }`}
          />
        ))}
      </div>
    </section>
  );
}
