export function formatDdMmmYyyy(timestamp) {
	return new Date(timestamp).toLocaleDateString('en-US', {
		year: 'numeric',
		month: 'short',
		day: 'numeric'
	});
}

export function formatLong(timestamp): string {
	return new Date(timestamp).toLocaleDateString('en-US', {
		weekday: 'long',
		year: 'numeric',
		month: 'long',
		day: 'numeric'
	});
}

export function formatShort(timestamp) {
	return new Date(timestamp).toLocaleString('en-US');
}

export function formatLocalTime(timestamp): string {
	return new Date(timestamp).toLocaleTimeString('en-US', { timeStyle: 'short' });
}

export function formatMillisToDate(millis: number) {
	return new Date(millis).toLocaleDateString('en-US');
}

export function isDateToday(timestamp): boolean {
	const today = new Date();
	const someDate = new Date(timestamp);
	return (
		someDate.getDate() === today.getDate() &&
		someDate.getMonth() === today.getMonth() &&
		someDate.getFullYear() === today.getFullYear()
	);
}

export function getElapsedTimeText(timestamp, showHours: boolean = false): string {
	const time = Date.now() - timestamp;
	const minutes = Math.floor(time / (1000 * 60));
	if (minutes > 0 && showHours) {
		const hours = Math.floor(time / (1000 * 60 * 60));
		return hours > 0 ? `${hours} hours ago` : `${minutes} minutes ago`;
	}
	return minutes > 0 ? `${minutes} minutes ago` : 'Just now';
}

export function formatTimestamp(timestamp) {
	const date = new Date(timestamp);

	const formatter = new Intl.DateTimeFormat('en-US', {
		year: 'numeric',
		month: 'long',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit'
	});

	return formatter.format(date);
}
