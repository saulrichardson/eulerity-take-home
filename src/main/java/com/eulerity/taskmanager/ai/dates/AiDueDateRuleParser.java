package com.eulerity.taskmanager.ai.dates;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.eulerity.taskmanager.ai.AiTaskInvalidOutputException;

@Component
public class AiDueDateRuleParser {

	private static final int MAX_NESTING_DEPTH = 3;

	private static final int MIN_PLUS_DAYS = 1;

	private static final int MAX_PLUS_DAYS = 366;

	private static final int MIN_PLUS_WEEKS = 1;

	private static final int MAX_PLUS_WEEKS = 52;

	private static final int MIN_NTH_NEXT = 1;

	private static final int MAX_NTH_NEXT = 8;

	private static final Pattern DATE = Pattern.compile("date\\((\\d{4}-\\d{2}-\\d{2})\\)");

	private static final Pattern MONTH_DAY = Pattern.compile("month_day\\((\\d{2})-(\\d{2})\\)");

	private static final Pattern FUNCTION = Pattern.compile("([a-z_]+)\\((.*)\\)");

	public AiDueDateRule parse(String expression) {
		if (expression == null || expression.isBlank()) {
			throw new AiTaskInvalidOutputException("AI response dueDateRule was missing");
		}
		if (!expression.equals(expression.strip()) || containsWhitespace(expression)) {
			throw unsupportedExpression();
		}
		return parseExpression(expression, 1);
	}

	private AiDueDateRule parseExpression(String expression, int depth) {
		if (depth > MAX_NESTING_DEPTH) {
			throw new AiTaskInvalidOutputException(
					"AI response dueDateRule nesting exceeded " + MAX_NESTING_DEPTH + " levels");
		}

		Matcher date = DATE.matcher(expression);
		if (date.matches()) {
			try {
				return new AiDueDateRule.AbsoluteDate(LocalDate.parse(date.group(1)));
			}
			catch (DateTimeParseException ex) {
				throw new AiTaskInvalidOutputException("AI response dueDateRule date was invalid", ex);
			}
		}

		Matcher monthDay = MONTH_DAY.matcher(expression);
		if (monthDay.matches()) {
			try {
				return new AiDueDateRule.MonthDayDate(
						MonthDay.of(Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2))));
			}
			catch (DateTimeException ex) {
				throw new AiTaskInvalidOutputException("AI response dueDateRule month_day was invalid", ex);
			}
		}

		Matcher function = FUNCTION.matcher(expression);
		if (!function.matches()) {
			throw unsupportedExpression();
		}

		String name = function.group(1);
		List<String> args = splitTopLevelArgs(function.group(2));
		return switch (name) {
			case "none" -> noArg(args, new AiDueDateRule.None());
			case "end_of_week" -> noArg(args, new AiDueDateRule.EndOfWeek());
			case "start_of_next_week" -> noArg(args, new AiDueDateRule.StartOfNextWeek());
			case "end_of_month" -> noArg(args, new AiDueDateRule.EndOfMonth());
			case "start_of_next_month" -> noArg(args, new AiDueDateRule.StartOfNextMonth());
			case "end_of_next_month" -> noArg(args, new AiDueDateRule.EndOfNextMonth());
			case "plus_days" -> plusDays(args, depth);
			case "plus_weeks" -> plusWeeks(args, depth);
			case "minus_days" -> minusDays(args, depth);
			case "minus_weeks" -> minusWeeks(args, depth);
			case "next_or_same" -> new AiDueDateRule.NextOrSame(singleWeekdayArg(args));
			case "next" -> new AiDueDateRule.Next(singleWeekdayArg(args));
			case "nth_next" -> nthNext(args);
			default -> throw unsupportedExpression();
		};
	}

	private AiDueDateRule plusDays(List<String> args, int depth) {
		if (args.size() == 1) {
			return new AiDueDateRule.PlusDays(parseBoundedAmount(args.get(0), "plus_days",
					MIN_PLUS_DAYS, MAX_PLUS_DAYS));
		}
		if (args.size() == 2) {
			return new AiDueDateRule.PlusDays(parseAnchor(args.get(0), depth),
					parseBoundedAmount(args.get(1), "plus_days", MIN_PLUS_DAYS, MAX_PLUS_DAYS));
		}
		throw unsupportedExpression();
	}

	private AiDueDateRule plusWeeks(List<String> args, int depth) {
		if (args.size() == 1) {
			return new AiDueDateRule.PlusWeeks(parseBoundedAmount(args.get(0), "plus_weeks",
					MIN_PLUS_WEEKS, MAX_PLUS_WEEKS));
		}
		if (args.size() == 2) {
			return new AiDueDateRule.PlusWeeks(parseAnchor(args.get(0), depth),
					parseBoundedAmount(args.get(1), "plus_weeks", MIN_PLUS_WEEKS, MAX_PLUS_WEEKS));
		}
		throw unsupportedExpression();
	}

	private AiDueDateRule minusDays(List<String> args, int depth) {
		if (args.size() != 2) {
			throw unsupportedExpression();
		}
		return new AiDueDateRule.MinusDays(parseAnchor(args.get(0), depth),
				parseBoundedAmount(args.get(1), "minus_days", MIN_PLUS_DAYS, MAX_PLUS_DAYS));
	}

	private AiDueDateRule minusWeeks(List<String> args, int depth) {
		if (args.size() != 2) {
			throw unsupportedExpression();
		}
		return new AiDueDateRule.MinusWeeks(parseAnchor(args.get(0), depth),
				parseBoundedAmount(args.get(1), "minus_weeks", MIN_PLUS_WEEKS, MAX_PLUS_WEEKS));
	}

	private AiDueDateRule nthNext(List<String> args) {
		if (args.size() != 2) {
			throw unsupportedExpression();
		}
		return new AiDueDateRule.NthNext(parseWeekday(args.get(0)),
				parseBoundedAmount(args.get(1), "nth_next", MIN_NTH_NEXT, MAX_NTH_NEXT));
	}

	private AiDueDateRule parseAnchor(String expression, int parentDepth) {
		AiDueDateRule anchor = parseExpression(expression, parentDepth + 1);
		if (anchor instanceof AiDueDateRule.None) {
			throw new AiTaskInvalidOutputException("AI response dueDateRule date arithmetic anchor cannot be none");
		}
		return anchor;
	}

	private <T extends AiDueDateRule> T noArg(List<String> args, T rule) {
		if (!args.isEmpty()) {
			throw unsupportedExpression();
		}
		return rule;
	}

	private DayOfWeek singleWeekdayArg(List<String> args) {
		if (args.size() != 1) {
			throw unsupportedExpression();
		}
		return parseWeekday(args.get(0));
	}

	private List<String> splitTopLevelArgs(String value) {
		if (value.isEmpty()) {
			return List.of();
		}
		List<String> args = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (current == '(') {
				depth++;
			}
			else if (current == ')') {
				depth--;
				if (depth < 0) {
					throw unsupportedExpression();
				}
			}
			else if (current == ',' && depth == 0) {
				addArg(args, value.substring(start, index));
				start = index + 1;
			}
		}
		if (depth != 0) {
			throw unsupportedExpression();
		}
		addArg(args, value.substring(start));
		return args;
	}

	private void addArg(List<String> args, String arg) {
		if (arg.isEmpty()) {
			throw unsupportedExpression();
		}
		args.add(arg);
	}

	private int parseBoundedAmount(String value, String functionName, int min, int max) {
		try {
			int amount = Integer.parseInt(value);
			if (amount < min || amount > max) {
				throw new AiTaskInvalidOutputException(
						"AI response dueDateRule " + functionName + " amount must be between " + min + " and " + max);
			}
			return amount;
		}
		catch (NumberFormatException ex) {
			throw unsupportedExpression();
		}
	}

	private DayOfWeek parseWeekday(String value) {
		try {
			return DayOfWeek.valueOf(value);
		}
		catch (IllegalArgumentException ex) {
			throw new AiTaskInvalidOutputException("AI response dueDateRule weekday was invalid", ex);
		}
	}

	private boolean containsWhitespace(String value) {
		return value.chars().anyMatch(Character::isWhitespace);
	}

	private AiTaskInvalidOutputException unsupportedExpression() {
		return new AiTaskInvalidOutputException(
				"AI response dueDateRule was not a supported date-rule expression");
	}
}
