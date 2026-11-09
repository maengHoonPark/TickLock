const wonFormat = new Intl.NumberFormat("ko-KR");

/** 12345678 → "12,345,678" */
export const formatNumber = (value: number): string => wonFormat.format(value);

/** 12345678 → "12,345,678원" */
export const formatWon = (value: number): string => `${wonFormat.format(value)}원`;
