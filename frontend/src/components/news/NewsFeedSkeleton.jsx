function SkeletonCard({ featured = false, index }) {
  return (
    <div className={["news-skeleton-card", featured ? "featured" : ""].filter(Boolean).join(" ")} key={index}>
      <div className="news-skeleton-media" />
      <div className="news-skeleton-body">
        <div className="news-skeleton-chip-row">
          <span className="news-skeleton-chip" />
          <span className="news-skeleton-chip short" />
        </div>
        <div className="news-skeleton-line wide" />
        <div className="news-skeleton-line medium" />
        <div className="news-skeleton-line" />
        <div className="news-skeleton-line short" />
      </div>
    </div>
  );
}

export default function NewsFeedSkeleton() {
  return (
    <section className="news-skeleton-stack" aria-hidden="true">
      <SkeletonCard featured index="featured" />
      <div className="news-grid news-grid-portal">
        {Array.from({ length: 6 }, (_, index) => (
          <SkeletonCard index={index} key={index} />
        ))}
      </div>
    </section>
  );
}
