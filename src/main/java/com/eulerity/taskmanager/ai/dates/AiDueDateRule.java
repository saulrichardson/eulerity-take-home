package com.eulerity.taskmanager.ai.dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;

public sealed interface AiDueDateRule
		permits AiDueDateRule.None, AiDueDateRule.AbsoluteDate, AiDueDateRule.MonthDayDate,
		AiDueDateRule.PlusDays, AiDueDateRule.PlusWeeks, AiDueDateRule.MinusDays,
		AiDueDateRule.MinusWeeks, AiDueDateRule.NextOrSame, AiDueDateRule.Next,
		AiDueDateRule.NthNext, AiDueDateRule.EndOfWeek, AiDueDateRule.StartOfNextWeek,
		AiDueDateRule.EndOfMonth, AiDueDateRule.StartOfNextMonth, AiDueDateRule.EndOfNextMonth {

	record None() implements AiDueDateRule {
	}

	record AbsoluteDate(LocalDate date) implements AiDueDateRule {
	}

	record MonthDayDate(MonthDay monthDay) implements AiDueDateRule {
	}

	record PlusDays(AiDueDateRule anchor, int days) implements AiDueDateRule {

		public PlusDays(int days) {
			this(null, days);
		}
	}

	record PlusWeeks(AiDueDateRule anchor, int weeks) implements AiDueDateRule {

		public PlusWeeks(int weeks) {
			this(null, weeks);
		}
	}

	record MinusDays(AiDueDateRule anchor, int days) implements AiDueDateRule {
	}

	record MinusWeeks(AiDueDateRule anchor, int weeks) implements AiDueDateRule {
	}

	record NextOrSame(DayOfWeek weekday) implements AiDueDateRule {
	}

	record Next(DayOfWeek weekday) implements AiDueDateRule {
	}

	record NthNext(DayOfWeek weekday, int occurrences) implements AiDueDateRule {
	}

	record EndOfWeek() implements AiDueDateRule {
	}

	record StartOfNextWeek() implements AiDueDateRule {
	}

	record EndOfMonth() implements AiDueDateRule {
	}

	record StartOfNextMonth() implements AiDueDateRule {
	}

	record EndOfNextMonth() implements AiDueDateRule {
	}
}
