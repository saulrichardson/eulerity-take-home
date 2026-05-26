package com.eulerity.taskmanager.ai.dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class AiDueDateSemanticValidator {

	public List<String> validationFailures(String sourceText, LocalDate dueDate, LocalDate today) {
		Objects.requireNonNull(today, "today must not be null");
		if (sourceText == null || sourceText.isBlank() || dueDate == null) {
			return List.of();
		}

		for (DayOfWeek weekday : DayOfWeek.values()) {
			if (containsBeforeWeekdayAfterNext(sourceText, weekday)) {
				LocalDate expectedDate = nthNextWeekday(weekday, today).minusDays(1);
				return expectedDateFailure(dueDate, expectedDate, "before " + weekday + " after next",
						"minus_days(nth_next(" + weekday + ",2),1)");
			}
			if (containsBeforeNextWeekday(sourceText, weekday)) {
				LocalDate expectedDate = nextCalendarWeekday(weekday, today).minusDays(1);
				return expectedDateFailure(dueDate, expectedDate, "before next " + weekday,
						"minus_days(next(" + weekday + "),1)");
			}
			if (containsBeforeThisOrBareWeekday(sourceText, weekday)) {
				LocalDate expectedDate = today.with(TemporalAdjusters.nextOrSame(weekday)).minusDays(1);
				return expectedDateFailure(dueDate, expectedDate, "before " + weekday,
						"minus_days(next_or_same(" + weekday + "),1)");
			}
			if (containsNotThisWeekdayButOneAfter(sourceText, weekday)) {
				LocalDate expectedDate = nextCalendarWeekday(weekday, today);
				return expectedDateFailure(dueDate, expectedDate, "not this " + weekday + " but the one after",
						"next(" + weekday + ")");
			}
			if (containsWeekdayAfterNext(sourceText, weekday)) {
				LocalDate expectedDate = nthNextWeekday(weekday, today);
				return expectedDateFailure(dueDate, expectedDate, weekday + " after next",
						"nth_next(" + weekday + ",2)");
			}
			if (containsExplicitNextWeekday(sourceText, weekday)) {
				LocalDate expectedDate = nextCalendarWeekday(weekday, today);
				return expectedDateFailure(dueDate, expectedDate, "explicit next " + weekday,
						"next(" + weekday + ")");
			}
		}

		return List.of();
	}

	private List<String> expectedDateFailure(LocalDate dueDate, LocalDate expectedDate, String phrase,
			String dateRule) {
		if (dueDate.equals(expectedDate)) {
			return List.of();
		}
		return List.of("dueDate resolved to " + dueDate + " for " + phrase + "; use " + dateRule
				+ " so it resolves to " + expectedDate);
	}

	private LocalDate nextCalendarWeekday(DayOfWeek weekday, LocalDate today) {
		return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
			.plusDays(weekday.getValue() - DayOfWeek.MONDAY.getValue());
	}

	private LocalDate nthNextWeekday(DayOfWeek weekday, LocalDate today) {
		return today.with(TemporalAdjusters.next(weekday)).plusWeeks(1);
	}

	private boolean containsBeforeWeekdayAfterNext(String sourceText, DayOfWeek weekday) {
		return contains(sourceText, "\\bbefore\\s+(?:the\\s+)?" + weekdayName(weekday) + "\\s+after\\s+next\\b");
	}

	private boolean containsBeforeNextWeekday(String sourceText, DayOfWeek weekday) {
		return contains(sourceText, "\\bbefore\\s+next\\s+" + weekdayName(weekday) + "\\b");
	}

	private boolean containsBeforeThisOrBareWeekday(String sourceText, DayOfWeek weekday) {
		return contains(sourceText, "\\bbefore\\s+(?:this\\s+)?" + weekdayName(weekday) + "\\b");
	}

	private boolean containsNotThisWeekdayButOneAfter(String sourceText, DayOfWeek weekday) {
		return contains(sourceText,
				"\\bnot\\s+this\\s+" + weekdayName(weekday) + "\\s+but\\s+(?:the\\s+)?one\\s+after\\b");
	}

	private boolean containsWeekdayAfterNext(String sourceText, DayOfWeek weekday) {
		return contains(sourceText, "\\b(?:the\\s+)?" + weekdayName(weekday) + "\\s+after\\s+next\\b");
	}

	private boolean containsExplicitNextWeekday(String sourceText, DayOfWeek weekday) {
		return contains(sourceText, "\\bnext\\s+" + weekdayName(weekday) + "\\b");
	}

	private boolean contains(String sourceText, String regex) {
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		return pattern.matcher(sourceText).find();
	}

	private String weekdayName(DayOfWeek weekday) {
		return weekday.name().toLowerCase(Locale.ROOT);
	}
}
