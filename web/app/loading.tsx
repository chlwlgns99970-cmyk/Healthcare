export default function Loading() {
  return (
    <main className="page-shell" aria-busy="true" aria-label="최신 버전 정보를 불러오는 중">
      <div className="page-frame">
        <section className="loading-card">
          <div className="skeleton skeleton-icon" />
          <div className="skeleton skeleton-title" />
          <div className="skeleton skeleton-line" />
          <div className="skeleton skeleton-button" />
          <p>최신 버전 정보를 불러오고 있어요.</p>
        </section>
      </div>
    </main>
  );
}
