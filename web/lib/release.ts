export type LatestRelease = {
  versionName: string;
  versionCode: number;
  platform: string;
  apkUrl: string;
  fileName: string;
  fileSizeBytes: number;
  sha256: string;
  releaseNotes: string[];
  releasedAt: string;
  minimumAndroidVersion: string | null;
};

type ReleaseRow = {
  version_name: unknown;
  version_code: unknown;
  platform: unknown;
  apk_url: unknown;
  file_name: unknown;
  file_size_bytes: unknown;
  sha256: unknown;
  release_notes: unknown;
  released_at: unknown;
  minimum_android_version: unknown;
};

export type ReleaseResult =
  | { status: "ready"; release: LatestRelease }
  | { status: "empty" }
  | { status: "error" };

function isGitHubReleaseAsset(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "github.com" && url.pathname.includes("/releases/download/");
  } catch {
    return false;
  }
}

function normalizeNotes(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
  }

  if (typeof value === "string") {
    return value
      .split(/\r?\n/)
      .map((item) => item.replace(/^[-•]\s*/, "").trim())
      .filter(Boolean);
  }

  return [];
}

function parseRelease(row: ReleaseRow): LatestRelease | null {
  const versionName = typeof row.version_name === "string" ? row.version_name : "";
  const versionCode = Number(row.version_code);
  const apkUrl = typeof row.apk_url === "string" ? row.apk_url : "";
  const fileName = typeof row.file_name === "string" ? row.file_name : "";
  const fileSizeBytes = Number(row.file_size_bytes);
  const sha256 = typeof row.sha256 === "string" ? row.sha256.toUpperCase() : "";
  const platform = typeof row.platform === "string" ? row.platform : "android";
  const releasedAt = typeof row.released_at === "string" ? row.released_at : "";

  if (
    !versionName ||
    !Number.isInteger(versionCode) ||
    !isGitHubReleaseAsset(apkUrl) ||
    !fileName.endsWith(".apk") ||
    !Number.isFinite(fileSizeBytes) ||
    fileSizeBytes <= 0 ||
    !/^[A-F0-9]{64}$/.test(sha256)
  ) {
    return null;
  }

  return {
    versionName,
    versionCode,
    platform,
    apkUrl,
    fileName,
    fileSizeBytes,
    sha256,
    releaseNotes: normalizeNotes(row.release_notes),
    releasedAt,
    minimumAndroidVersion:
      typeof row.minimum_android_version === "string" ? row.minimum_android_version : null,
  };
}

export async function getLatestRelease(): Promise<ReleaseResult> {
  const supabaseUrl = process.env.SUPABASE_URL;
  const anonKey = process.env.SUPABASE_ANON_KEY;

  if (!supabaseUrl || !anonKey) {
    return { status: "error" };
  }

  try {
    const endpoint = new URL("/rest/v1/app_releases", supabaseUrl);
    endpoint.searchParams.set("select", "version_name,version_code,platform,apk_url,file_name,file_size_bytes,sha256,release_notes,released_at,minimum_android_version");
    endpoint.searchParams.set("platform", "eq.android");
    endpoint.searchParams.set("status", "eq.published");
    endpoint.searchParams.set("is_latest", "eq.true");
    endpoint.searchParams.set("order", "released_at.desc");
    endpoint.searchParams.set("limit", "1");

    const response = await fetch(endpoint, {
      headers: {
        apikey: anonKey,
        Authorization: `Bearer ${anonKey}`,
      },
      cache: "no-store",
    });

    if (!response.ok) {
      return { status: "error" };
    }

    const rows = (await response.json()) as ReleaseRow[];
    if (!rows.length) {
      return { status: "empty" };
    }

    const release = parseRelease(rows[0]);
    return release ? { status: "ready", release } : { status: "error" };
  } catch {
    return { status: "error" };
  }
}
