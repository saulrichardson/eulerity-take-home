package com.eulerity.taskmanager.ai.dates;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class AiDueDateSemanticValidatorTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 5, 25);

	private final AiDueDateSemanticValidator validator = new AiDueDateSemanticValidator();

	@Test
	void rejectsExplicitNextCurrentWeekdayResolvedToToday() {
		assertThat(this.validator.validationFailures("follow up with finance next Monday", MONDAY, MONDAY))
			.containsExactly("dueDate resolved to 2026-05-25 for explicit next MONDAY; "
					+ "use next(MONDAY) so it resolves to 2026-06-01");
	}

	@Test
	void rejectsExplicitNextWeekdayResolvedToCurrentWeekOccurrence() {
		assertThat(this.validator.validationFailures("send the client update next Friday",
				LocalDate.of(2026, 5, 29), MONDAY))
			.containsExactly("dueDate resolved to 2026-05-29 for explicit next FRIDAY; "
					+ "use next(FRIDAY) so it resolves to 2026-06-05");
	}

	@Test
	void allowsExplicitNextWeekdayResolvedToNextCalendarWeek() {
		assertThat(this.validator.validationFailures("follow up with finance next Monday",
				LocalDate.of(2026, 6, 1), MONDAY)).isEmpty();
		assertThat(this.validator.validationFailures("send the client update next Friday",
				LocalDate.of(2026, 6, 5), MONDAY)).isEmpty();
	}

	@Test
	void rejectsBeforeWeekdayResolvedToNamedDay() {
		assertThat(this.validator.validationFailures("submit the quarterly report before Friday",
				LocalDate.of(2026, 5, 29), MONDAY))
			.containsExactly("dueDate resolved to 2026-05-29 for before FRIDAY; "
					+ "use minus_days(next_or_same(FRIDAY),1) so it resolves to 2026-05-28");
	}

	@Test
	void allowsBeforeWeekdayResolvedToDayBeforeNamedDay() {
		assertThat(this.validator.validationFailures("submit the quarterly report before Friday",
				LocalDate.of(2026, 5, 28), MONDAY)).isEmpty();
		assertThat(this.validator.validationFailures("submit the quarterly report before this Friday",
				LocalDate.of(2026, 5, 28), MONDAY)).isEmpty();
	}

	@Test
	void rejectsBeforeNextWeekdayResolvedToCurrentWeek() {
		assertThat(this.validator.validationFailures("submit the quarterly report before next Friday",
				LocalDate.of(2026, 5, 28), MONDAY))
			.containsExactly("dueDate resolved to 2026-05-28 for before next FRIDAY; "
					+ "use minus_days(next(FRIDAY),1) so it resolves to 2026-06-04");
	}

	@Test
	void allowsBeforeNextWeekdayResolvedToDayBeforeNextCalendarWeekday() {
		assertThat(this.validator.validationFailures("submit the quarterly report before next Friday",
				LocalDate.of(2026, 6, 4), MONDAY)).isEmpty();
	}

	@Test
	void rejectsNotThisWeekdayButOneAfterResolvedToThisWeekday() {
		assertThat(this.validator.validationFailures("send the client update not this Friday but the one after",
				LocalDate.of(2026, 5, 29), MONDAY))
			.containsExactly("dueDate resolved to 2026-05-29 for not this FRIDAY but the one after; "
					+ "use next(FRIDAY) so it resolves to 2026-06-05");
	}

	@Test
	void rejectsWeekdayAfterNextResolvedToFirstUpcomingWeekday() {
		assertThat(this.validator.validationFailures("send the client update the Friday after next",
				LocalDate.of(2026, 5, 29), MONDAY))
			.containsExactly("dueDate resolved to 2026-05-29 for FRIDAY after next; "
					+ "use nth_next(FRIDAY,2) so it resolves to 2026-06-05");
	}

	@Test
	void rejectsBeforeWeekdayAfterNextResolvedToNamedDay() {
		assertThat(this.validator.validationFailures("submit the report before the Friday after next",
				LocalDate.of(2026, 6, 5), MONDAY))
			.containsExactly("dueDate resolved to 2026-06-05 for before FRIDAY after next; "
					+ "use minus_days(nth_next(FRIDAY,2),1) so it resolves to 2026-06-04");
	}

	@Test
	void allowsThisOnAndByCurrentWeekdayResolvedToToday() {
		assertThat(this.validator.validationFailures("follow up with finance this Monday", MONDAY, MONDAY))
			.isEmpty();
		assertThat(this.validator.validationFailures("follow up with finance on Monday", MONDAY, MONDAY))
			.isEmpty();
		assertThat(this.validator.validationFailures("follow up with finance by Monday", MONDAY, MONDAY))
			.isEmpty();
	}

	@Test
	void ignoresUnmatchedOtherWeekdayPhrases() {
		assertThat(this.validator.validationFailures("send the client update by Friday",
				LocalDate.of(2026, 5, 29), MONDAY)).isEmpty();
	}

	@Test
	void ignoresNullDueDateBecauseMissingDatesAreHandledBySuggestionDraftRules() {
		assertThat(this.validator.validationFailures("follow up with finance next Monday", null, MONDAY)).isEmpty();
	}
}
