import { NextResponse } from "next/server";

import { getLatestRelease } from "@/lib/release";

export const dynamic = "force-dynamic";

const NO_STORE_HEADERS = {
  "Cache-Control": "no-store, max-age=0",
};

export async function GET() {
  const result = await getLatestRelease();

  if (result.status === "empty") {
    return NextResponse.json(
      { error: "No published Android release is available." },
      { status: 404, headers: NO_STORE_HEADERS },
    );
  }

  if (result.status === "error") {
    return NextResponse.json(
      { error: "Release information is temporarily unavailable." },
      { status: 503, headers: NO_STORE_HEADERS },
    );
  }

  const { release } = result;
  return NextResponse.json(
    {
      versionName: release.versionName,
      versionCode: release.versionCode,
      apkUrl: release.apkUrl,
      fileName: release.fileName,
      fileSizeBytes: release.fileSizeBytes,
      sha256: release.sha256,
      releaseNotes: release.releaseNotes,
      releasedAt: release.releasedAt,
    },
    { headers: NO_STORE_HEADERS },
  );
}
