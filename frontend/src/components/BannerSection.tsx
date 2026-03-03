import BannerCarousel from "./BannerCarousel";

export default function BannerSection() {
  return (
    <section className="w-[95%] mx-auto mb-6">
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_minmax(630px,840px)_1fr] gap-5 items-stretch">
        <div className="hidden lg:block w-full max-w-[280px] h-full justify-self-start rounded-2xl bg-slate-200 border border-slate-300" />

        <div className="w-full aspect-[4/2]">
          <BannerCarousel />
        </div>

        <div className="hidden lg:block w-full max-w-[280px] h-full justify-self-end rounded-2xl bg-slate-200 border border-slate-300" />
      </div>
    </section>
  );
}
