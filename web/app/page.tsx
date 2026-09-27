import Image from "next/image";
import { CopyHashButton } from "./copy-hash-button";
import { getLatestRelease, type LatestRelease } from "@/lib/release";

export const dynamic = "force-dynamic";

function formatFileSize(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function DownloadIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none">
      <path d="M12 3v11m0 0 4-4m-4 4-4-4M5 20h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="none">
      <path d="m5 12 4 4L19 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function ReleaseContent({ release }: { release: LatestRelease }) {
  return (
    <>
      <section className="version-card" aria-labelledby="version-heading">
        <div>
          <p className="eyebrow" id="version-heading">최신 버전</p>
          <div className="version-line">
            <strong>{release.versionName}</strong>
            <span>{release.platform.toLowerCase() === "android" ? "Android" : release.platform}</span>
          </div>
        </div>
        <div className="file-size">
          <span>파일 크기</span>
          <strong>{formatFileSize(release.fileSizeBytes)}</strong>
        </div>
      </section>

      <a className="download-button" href={release.apkUrl} aria-label={`오늘 뭐먹지 ${release.versionName} Android APK 다운로드`}>
        <DownloadIcon />
        Android APK 다운로드
      </a>
      <p className="file-name">{release.fileName}</p>

      <section className="update-notice" aria-labelledby="update-title">
        <div className="notice-icon"><CheckIcon /></div>
        <div>
          <h2 id="update-title">기존 사용자는 앱을 삭제하지 마세요.</h2>
          <p>새 APK를 설치하면 기존 앱이 업데이트되며 기존 기록은 유지됩니다.</p>
        </div>
      </section>

      <section className="content-card" aria-labelledby="changes-title">
        <p className="section-kicker">이번 버전</p>
        <h2 id="changes-title">달라진 점</h2>
        {release.releaseNotes.length ? (
          <ul className="release-list">
            {release.releaseNotes.map((note) => (
              <li key={note}><CheckIcon /><span>{note}</span></li>
            ))}
          </ul>
        ) : (
          <p className="muted">등록된 변경사항이 없습니다.</p>
        )}
      </section>

      <section className="content-card" aria-labelledby="install-title">
        <p className="section-kicker">설치 안내</p>
        <h2 id="install-title">3단계로 설치하세요</h2>
        <ol className="install-list">
          <li><span>1</span><div><strong>APK 다운로드</strong><p>위의 초록색 버튼을 눌러 파일을 받으세요.</p></div></li>
          <li><span>2</span><div><strong>다운로드한 APK 열기</strong><p>브라우저나 파일 앱에서 받은 파일을 여세요.</p></div></li>
          <li><span>3</span><div><strong>설치 또는 업데이트 선택</strong><p>처음 설치한다면 ‘설치’, 기존 사용자라면 ‘업데이트’를 누르세요.</p></div></li>
        </ol>
        <p className="permission-note">처음 직접 설치할 때는 브라우저 또는 파일 앱에 ‘출처를 알 수 없는 앱 설치 허용’ 화면이 나타날 수 있어요.</p>
      </section>

      <details className="hash-card">
        <summary>SHA-256 확인</summary>
        <div className="hash-content">
          <code>{release.sha256}</code>
          <CopyHashButton value={release.sha256} />
        </div>
      </details>
    </>
  );
}

export default async function Home() {
  const result = await getLatestRelease();

  return (
    <main className="page-shell">
      <div className="page-frame">
        <header className="hero">
          <div className="icon-wrap">
            <Image src="/app-icon.png" width={152} height={152} priority alt="오늘 뭐먹지 앱 아이콘" />
          </div>
          <p className="brand-label">오늘의 건강한 선택</p>
          <h1>오늘 뭐먹지</h1>
          <p className="hero-copy">오늘 먹을 메뉴부터<br />칼로리·기록·추천까지</p>
        </header>

        {result.status === "ready" ? (
          <ReleaseContent release={result.release} />
        ) : result.status === "empty" ? (
          <section className="state-card" role="status">
            <h2>현재 다운로드 가능한 버전이 없습니다.</h2>
            <p>새 버전이 준비되면 이곳에서 안내해 드릴게요.</p>
          </section>
        ) : (
          <section className="state-card error-state" role="alert">
            <h2>최신 버전 정보를 불러오지 못했습니다.</h2>
            <p>잠시 후 다시 시도해 주세요. 안전을 위해 다운로드 버튼은 표시하지 않았습니다.</p>
          </section>
        )}

        <footer>
          <p>오늘 뭐먹지 · Android 앱</p>
          <p>음식 칼로리와 운동 소모량은 추정값이며 의료 진단을 대신하지 않습니다.</p>
        </footer>
      </div>
    </main>
  );
}
