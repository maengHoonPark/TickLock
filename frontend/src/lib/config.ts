/** 백엔드 주소. 배포 환경에서는 NEXT_PUBLIC_API_URL 환경변수로 바꾼다. */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
