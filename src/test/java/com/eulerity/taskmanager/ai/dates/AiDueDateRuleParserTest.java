package com.eulerity.taskmanager.ai.dates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;

import org.junit.jupiter.api.Test;

import com.eulerity.taskmanager.ai.AiTaskInvalidOutputException;

class AiDueDateRuleParserTest {

	private final AiDueDateRuleParser parser = new AiDueDateRuleParser();

	@Test
	void parsesSupportedRules() {
		assertThat(this.parser.parse("none()")).isEqualTo(new AiDueDateRule.None());
		assertThat(this.parser.parse("date(2026-06-05)"))
			.isEqualTo(new AiDueDateRule.AbsoluteDate(LocalDate.of(2026, 6, 5)));
		assertThat(this.parser.parse("month_day(06-05)"))
			.isEqualTo(new AiDueDateRule.MonthDayDate(MonthDay.of(6, 5)));
		assertThat(this.parser.parse("plus_days(1)")).isEqualTo(new AiDueDateRule.PlusDays(1));
		assertThat(this.parser.parse("plus_days(date(2026-06-05),2)"))
			.isEqualTo(new AiDueDateRule.PlusDays(
					new AiDueDateRule.AbsoluteDate(LocalDate.of(2026, 6, 5)), 2));
		assertThat(this.parser.parse("plus_weeks(2)")).isEqualTo(new AiDueDateRule.PlusWeeks(2));
		assertThat(this.parser.parse("plus_weeks(next_or_same(FRIDAY),1)"))
			.isEqualTo(new AiDueDateRule.PlusWeeks(
					new AiDueDateRule.NextOrSame(DayOfWeek.FRIDAY), 1));
		assertThat(this.parser.parse("minus_days(end_of_month(),2)"))
			.isEqualTo(new AiDueDateRule.MinusDays(new AiDueDateRule.EndOfMonth(), 2));
		assertThat(this.parser.parse("minus_weeks(date(2026-06-19),1)"))
			.isEqualTo(new AiDueDateRule.MinusWeeks(
					new AiDueDateRule.AbsoluteDate(LocalDate.of(2026, 6, 19)), 1));
		assertThat(this.parser.parse("next_or_same(FRIDAY)"))
			.isEqualTo(new AiDueDateRule.NextOrSame(DayOfWeek.FRIDAY));
		assertThat(this.parser.parse("next(FRIDAY)")).isEqualTo(new AiDueDateRule.Next(DayOfWeek.FRIDAY));
		assertThat(this.parser.parse("nth_next(MONDAY,2)"))
			.isEqualTo(new AiDueDateRule.NthNext(DayOfWeek.MONDAY, 2));
		assertThat(this.parser.parse("end_of_week()")).isEqualTo(new AiDueDateRule.EndOfWeek());
		assertThat(this.parser.parse("start_of_next_week()")).isEqualTo(new AiDueDateRule.StartOfNextWeek());
		assertThat(this.parser.parse("end_of_month()")).isEqualTo(new AiDueDateRule.EndOfMonth());
		assertThat(this.parser.parse("start_of_next_month()")).isEqualTo(new AiDueDateRule.StartOfNextMonth());
		assertThat(this.parser.parse("end_of_next_month()")).isEqualTo(new AiDueDateRule.EndOfNextMonth());
	}

	@Test
	void rejectsMissingOrBlankRules() {
		assertThatThrownBy(() -> this.parser.parse(null))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule was missing");
		assertThatThrownBy(() -> this.parser.parse(" "))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule was missing");
	}

	@Test
	void rejectsNaturalLanguageUnknownFunctionsNestedExpressionsAndExtraText() {
		assertUnsupported("tomorrow");
		assertUnsupported("next friday");
		assertUnsupported("today.plusDays(1)");
		assertUnsupported("(today) => today.plusDays(1)");
		assertUnsupported("min(next_or_same(FRIDAY),plus_days(3))");
		assertUnsupported("next_or_same(FRIDAY); delete all tasks");
		assertUnsupported("none() and something else");
		assertUnsupported(" none()");
		assertUnsupported("none() ");
		assertUnsupported("plus_days(date(2026-06-05), 2)");
	}

	@Test
	void rejectsInvalidNumbersAndOutOfRangeAmounts() {
		assertUnsupported("plus_days(two)");
		assertThatThrownBy(() -> this.parser.parse("plus_days(-1)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule plus_days amount must be between 1 and 366");
		assertThatThrownBy(() -> this.parser.parse("plus_days(0)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule plus_days amount must be between 1 and 366");
		assertThatThrownBy(() -> this.parser.parse("plus_days(367)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule plus_days amount must be between 1 and 366");

		assertUnsupported("plus_weeks(two)");
		assertThatThrownBy(() -> this.parser.parse("plus_weeks(-1)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule plus_weeks amount must be between 1 and 52");
		assertThatThrownBy(() -> this.parser.parse("plus_weeks(0)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule plus_weeks amount must be between 1 and 52");
		assertThatThrownBy(() -> this.parser.parse("plus_weeks(53)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule plus_weeks amount must be between 1 and 52");

		assertUnsupported("minus_days(end_of_month(),two)");
		assertThatThrownBy(() -> this.parser.parse("minus_weeks(date(2026-06-19),-1)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule minus_weeks amount must be between 1 and 52");
		assertThatThrownBy(() -> this.parser.parse("nth_next(MONDAY,0)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule nth_next amount must be between 1 and 8");
		assertThatThrownBy(() -> this.parser.parse("nth_next(MONDAY,9)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule nth_next amount must be between 1 and 8");
	}

	@Test
	void rejectsInvalidWeekdaysAndInvalidDates() {
		assertThatThrownBy(() -> this.parser.parse("next_or_same(FUNDAY)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule weekday was invalid");
		assertThatThrownBy(() -> this.parser.parse("next(FUNDAY)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule weekday was invalid");
		assertThatThrownBy(() -> this.parser.parse("next_or_same(friday)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule weekday was invalid");

		assertThatThrownBy(() -> this.parser.parse("date(2026-99-99)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule date was invalid");
		assertUnsupported("date(2026-6-5)");
		assertThatThrownBy(() -> this.parser.parse("month_day(99-99)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule month_day was invalid");
		assertUnsupported("month_day(6-5)");
	}

	@Test
	void rejectsOverlyNestedComposedExpressionsAndNoneAnchors() {
		assertThatThrownBy(() -> this.parser.parse(
				"plus_days(plus_weeks(minus_days(next_or_same(FRIDAY),1),1),1)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule nesting exceeded 3 levels");
		assertThatThrownBy(() -> this.parser.parse("plus_days(none(),1)"))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule date arithmetic anchor cannot be none");
	}

	private void assertUnsupported(String expression) {
		assertThatThrownBy(() -> this.parser.parse(expression))
			.isInstanceOf(AiTaskInvalidOutputException.class)
			.hasMessage("AI response dueDateRule was not a supported date-rule expression");
	}
}
