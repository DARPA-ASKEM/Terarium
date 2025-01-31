import { CalendarDateType } from '@/types/common';

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

export function getElapsedTimeText(timestamp): string {
	const time = Date.now() - new Date(timestamp).getTime();
	const minutes = Math.floor(time / (1000 * 60));
	const hours = Math.floor(time / (1000 * 60 * 60));
	if (hours > 24) {
		return formatDdMmmYyyy(timestamp);
	}
	if (hours > 0 && hours < 25) {
		return `${hours} hours ago`;
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

// Sorts dates in descending order. To be used with Array.sort().
export function sortDatesDesc(a, b) {
	return new Date(b).getTime() - new Date(a).getTime();
}
// Sorts dates in ascending order. To be used with Array.sort().
export function sortDatesAsc(a, b) {
	return new Date(a).getTime() - new Date(b).getTime();
}
export interface CalendarSettings {
	view: CalendarDateType;
	format: string;
}

export function getTimestepFromDateRange(startDate: Date, endDate: Date, stepType: CalendarDateType): number {
	startDate = new Date(startDate);
	const diffInMilliseconds = endDate.getTime() - startDate.getTime();

	switch (stepType) {
		case 'month':
			return (endDate.getFullYear() - startDate.getFullYear()) * 12 + (endDate.getMonth() - startDate.getMonth());
		case 'year':
			return endDate.getFullYear() - startDate.getFullYear();
		case 'date':
		default:
			return Math.floor(diffInMilliseconds / (1000 * 60 * 60 * 24));
	}
}

export function getEndDateFromTimestep(startDate: Date, timestep: number, stepType: CalendarDateType): Date {
	const endDate = new Date(startDate);

	switch (stepType) {
		case 'month':
			endDate.setMonth(endDate.getMonth() + timestep);
			break;
		case 'year':
			endDate.setFullYear(endDate.getFullYear() + timestep);
			break;
		case 'date':
		default:
			endDate.setDate(endDate.getDate() + timestep);
			break;
	}

	return endDate;
}

export function getTimePointString(
	timePoint: number,
	options: { startDate?: Date; calendarSettings?: CalendarSettings }
): string {
	if (!options.startDate) {
		return `${timePoint?.toString()} day.`;
	}

	const view = options.calendarSettings?.view ?? CalendarDateType.DATE;
	const date = getEndDateFromTimestep(options?.startDate, timePoint, view);

	const dateOptions: Intl.DateTimeFormatOptions = {
		year: 'numeric',
		...(view === CalendarDateType.DATE && { month: 'long', day: 'numeric' }),
		...(view === CalendarDateType.MONTH && { month: 'long' })
	};

	return `${date.toLocaleDateString('default', dateOptions)}.`;
}
