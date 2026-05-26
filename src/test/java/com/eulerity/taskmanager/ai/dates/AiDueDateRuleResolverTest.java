package com.eulerity.taskmanager.ai.dates;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;

import org.junit.jupiter.api.Test;

class AiDueDateRuleResolverTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 5, 25);

	private final AiDueDateRuleResolver resolver = new AiDueDateRuleResolver();

	@Test
	void resolvesSupportedRulesAgainstToday() {
		assertThat(this.resolver.resolve(new AiDueDateRule.None(), TODAY)).isNull();
		assertThat(this.resolver.resolve(new AiDueDateRule.AbsoluteDate(LocalDate.of(2026, 6, 5)), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 5));
		assertThat(this.resolver.resolve(new AiDueDateRule.MonthDayDate(MonthDay.of(6, 5)), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 5));
		assertThat(this.resolver.resolve(new AiDueDateRule.MonthDayDate(MonthDay.of(5, 24)), TODAY))
			.isEqualTo(LocalDate.of(2027, 5, 24));
		assertThat(this.resolver.resolve(new AiDueDateRule.MonthDayDate(MonthDay.of(5, 25)), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 25));
		assertThat(this.resolver.resolve(new AiDueDateRule.MonthDayDate(MonthDay.of(2, 29)), TODAY))
			.isEqualTo(LocalDate.of(2028, 2, 29));
		assertThat(this.resolver.resolve(new AiDueDateRule.PlusDays(1), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 26));
		assertThat(this.resolver.resolve(new AiDueDateRule.PlusDays(
				new AiDueDateRule.AbsoluteDate(LocalDate.of(2026, 6, 5)), 2), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 7));
		assertThat(this.resolver.resolve(new AiDueDateRule.PlusWeeks(2), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 8));
		assertThat(this.resolver.resolve(new AiDueDateRule.PlusWeeks(
				new AiDueDateRule.NextOrSame(DayOfWeek.FRIDAY), 1), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 5));
		assertThat(this.resolver.resolve(new AiDueDateRule.MinusDays(
				new AiDueDateRule.EndOfMonth(), 2), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 29));
		assertThat(this.resolver.resolve(new AiDueDateRule.MinusWeeks(
				new AiDueDateRule.AbsoluteDate(LocalDate.of(2026, 6, 19)), 1), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 12));
		assertThat(this.resolver.resolve(new AiDueDateRule.NextOrSame(DayOfWeek.FRIDAY), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 29));
		assertThat(this.resolver.resolve(new AiDueDateRule.NextOrSame(DayOfWeek.MONDAY), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 25));
		assertThat(this.resolver.resolve(new AiDueDateRule.Next(DayOfWeek.FRIDAY), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 5));
		assertThat(this.resolver.resolve(new AiDueDateRule.Next(DayOfWeek.MONDAY), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 1));
		assertThat(this.resolver.resolve(new AiDueDateRule.Next(DayOfWeek.TUESDAY), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 2));
		assertThat(this.resolver.resolve(new AiDueDateRule.MinusDays(
				new AiDueDateRule.NextOrSame(DayOfWeek.FRIDAY), 1), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 28));
		assertThat(this.resolver.resolve(new AiDueDateRule.MinusDays(
				new AiDueDateRule.Next(DayOfWeek.FRIDAY), 1), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 4));
		assertThat(this.resolver.resolve(new AiDueDateRule.NthNext(DayOfWeek.MONDAY, 2), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 8));
		assertThat(this.resolver.resolve(new AiDueDateRule.NthNext(DayOfWeek.FRIDAY, 2), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 5));
		assertThat(this.resolver.resolve(new AiDueDateRule.EndOfWeek(), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 31));
		assertThat(this.resolver.resolve(new AiDueDateRule.StartOfNextWeek(), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 1));
		assertThat(this.resolver.resolve(new AiDueDateRule.EndOfMonth(), TODAY))
			.isEqualTo(LocalDate.of(2026, 5, 31));
		assertThat(this.resolver.resolve(new AiDueDateRule.StartOfNextMonth(), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 1));
		assertThat(this.resolver.resolve(new AiDueDateRule.EndOfNextMonth(), TODAY))
			.isEqualTo(LocalDate.of(2026, 6, 30));
	}

	@Test
	void endOfWeekMeansSunday() {
		assertThat(this.resolver.resolve(new AiDueDateRule.EndOfWeek(), LocalDate.of(2026, 5, 29)))
			.isEqualTo(LocalDate.of(2026, 5, 31));
		assertThat(this.resolver.resolve(new AiDueDateRule.EndOfWeek(), LocalDate.of(2026, 5, 30)))
			.isEqualTo(LocalDate.of(2026, 5, 31));
		assertThat(this.resolver.resolve(new AiDueDateRule.EndOfWeek(), LocalDate.of(2026, 5, 31)))
			.isEqualTo(LocalDate.of(2026, 5, 31));
	}
}
