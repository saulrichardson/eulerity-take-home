package com.eulerity.taskmanager.ai.dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class AiDueDateRuleResolver {

	public LocalDate resolve(AiDueDateRule rule, LocalDate today) {
		Objects.requireNonNull(rule, "rule must not be null");
		Objects.requireNonNull(today, "today must not be null");

		if (rule instanceof AiDueDateRule.None) {
			return null;
		}
		if (rule instanceof AiDueDateRule.AbsoluteDate absoluteDate) {
			return absoluteDate.date();
		}
		if (rule instanceof AiDueDateRule.MonthDayDate monthDayDate) {
			return resolveMonthDay(monthDayDate.monthDay(), today);
		}
		if (rule instanceof AiDueDateRule.PlusDays plusDays) {
			return resolveAnchor(plusDays.anchor(), today).plusDays(plusDays.days());
		}
		if (rule instanceof AiDueDateRule.PlusWeeks plusWeeks) {
			return resolveAnchor(plusWeeks.anchor(), today).plusWeeks(plusWeeks.weeks());
		}
		if (rule instanceof AiDueDateRule.MinusDays minusDays) {
			return resolveAnchor(minusDays.anchor(), today).minusDays(minusDays.days());
		}
		if (rule instanceof AiDueDateRule.MinusWeeks minusWeeks) {
			return resolveAnchor(minusWeeks.anchor(), today).minusWeeks(minusWeeks.weeks());
		}
		if (rule instanceof AiDueDateRule.NextOrSame nextOrSame) {
			return today.with(TemporalAdjusters.nextOrSame(nextOrSame.weekday()));
		}
		if (rule instanceof AiDueDateRule.Next next) {
			return resolveNextWeekday(next.weekday(), today);
		}
		if (rule instanceof AiDueDateRule.NthNext nthNext) {
			return today.with(TemporalAdjusters.next(nthNext.weekday())).plusWeeks(nthNext.occurrences() - 1L);
		}
		if (rule instanceof AiDueDateRule.EndOfWeek) {
			return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		}
		if (rule instanceof AiDueDateRule.StartOfNextWeek) {
			return startOfNextWeek(today);
		}
		if (rule instanceof AiDueDateRule.EndOfMonth) {
			return today.with(TemporalAdjusters.lastDayOfMonth());
		}
		if (rule instanceof AiDueDateRule.StartOfNextMonth) {
			return today.with(TemporalAdjusters.firstDayOfNextMonth());
		}
		if (rule instanceof AiDueDateRule.EndOfNextMonth) {
			return today.with(TemporalAdjusters.firstDayOfNextMonth()).with(TemporalAdjusters.lastDayOfMonth());
		}

		throw new IllegalArgumentException("Unsupported AI due-date rule: " + rule);
	}

	private LocalDate resolveAnchor(AiDueDateRule anchor, LocalDate today) {
		if (anchor == null) {
			return today;
		}
		LocalDate resolved = resolve(anchor, today);
		if (resolved == null) {
			throw new IllegalArgumentException("AI due-date arithmetic anchor resolved to no date");
		}
		return resolved;
	}

	private LocalDate resolveMonthDay(MonthDay monthDay, LocalDate today) {
		for (int year = today.getYear(); year <= today.getYear() + 8; year++) {
			if (monthDay.isValidYear(year)) {
				LocalDate candidate = monthDay.atYear(year);
				if (!candidate.isBefore(today)) {
					return candidate;
				}
			}
		}
		throw new IllegalArgumentException("Unable to resolve AI month-day rule: " + monthDay);
	}

	private LocalDate resolveNextWeekday(DayOfWeek weekday, LocalDate today) {
		return startOfNextWeek(today).plusDays(weekday.getValue() - DayOfWeek.MONDAY.getValue());
	}

	private LocalDate startOfNextWeek(LocalDate today) {
		return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
	}
}
