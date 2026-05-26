package com.eulerity.taskmanager.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.eulerity.taskmanager.ai.AiTaskSuggestionPrompt;
import com.eulerity.taskmanager.ai.TaskAiClient;
import com.eulerity.taskmanager.ai.TaskAiTokenCounter;
import com.eulerity.taskmanager.dto.TaskSuggestionResponse;
import com.eulerity.taskmanager.model.TaskPriority;
import com.eulerity.taskmanager.model.TaskStatus;
import com.eulerity.taskmanager.repository.TaskRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskSuggestionUiSmokeTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TaskRepository taskRepository;

	@MockitoBean
	private TaskAiClient taskAiClient;

	@MockitoBean
	private TaskAiTokenCounter tokenCounter;

	private WebDriver driver;

	private WebDriverWait wait;

	@BeforeEach
	void setUp() {
		this.taskRepository.deleteAll();
		lenient().when(this.tokenCounter.countTaskSuggestionInputTokens(any(AiTaskSuggestionPrompt.class)))
			.thenReturn(100L);
		this.driver = startChromeOrSkip();
		this.wait = new WebDriverWait(this.driver, Duration.ofSeconds(8));
	}

	@AfterEach
	void tearDown() {
		if (this.driver != null) {
			this.driver.quit();
		}
	}

	@Test
	void noDateAiSuggestionCanBeReviewedEditedAndCreatedAfterDueDateIsAdded() {
		when(this.taskAiClient.suggestTask(any(AiTaskSuggestionPrompt.class)))
			.thenReturn(new TaskSuggestionResponse("Review launch notes",
					"Review launch notes and capture follow-up items.", null, TaskPriority.MEDIUM, TaskStatus.TODO));

		this.driver.get("http://localhost:" + this.port + "/");
		waitForTaskCount("0 tasks");

		WebElement suggestionText = this.driver.findElement(By.id("suggestDescription"));
		suggestionText.clear();
		suggestionText.sendKeys("Review the launch notes and capture follow-up items. No due date was discussed.");
		this.driver.findElement(By.id("suggestButton")).click();

		this.wait.until(
				ExpectedConditions.textToBePresentInElementLocated(By.id("suggestionOutput"), "Review launch notes"));
		WebElement suggestionOutput = this.driver.findElement(By.id("suggestionOutput"));
		assertThat(suggestionOutput.getText()).contains("Review launch notes")
			.contains("Review launch notes and capture follow-up items.")
			.contains("No due date")
			.contains("Add a due date in the task form before creating this task.");

		WebElement directCreate = this.driver.findElement(
				By.xpath("//*[@id='suggestionOutput']//button[normalize-space()='Create Task']"));
		assertThat(directCreate.isEnabled()).isFalse();
		assertThat(this.driver.findElement(By.id("taskCount")).getText()).isEqualTo("0 tasks");

		this.driver.findElement(By.xpath("//*[@id='suggestionOutput']//button[normalize-space()='Edit in Form']"))
			.click();
		this.wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("formMessage"),
				"Suggestion copied into the task form."));

		assertThat(value("title")).isEqualTo("Review launch notes");
		assertThat(value("description")).isEqualTo("Review launch notes and capture follow-up items.");
		assertThat(value("dueDate")).isBlank();
		assertThat(value("priority")).isEqualTo("MEDIUM");
		assertThat(value("status")).isEqualTo("TODO");
		assertThat(js("return document.querySelector('#dueDate').validity.valueMissing;")).isEqualTo(Boolean.TRUE);

		this.driver.findElement(By.cssSelector("#taskForm button[type='submit']")).click();
		assertThat(this.driver.findElement(By.id("taskCount")).getText()).isEqualTo("0 tasks");
		assertThat(this.taskRepository.count()).isZero();

		js("""
				const input = document.querySelector('#dueDate');
				input.value = '2026-06-08';
				input.dispatchEvent(new Event('input', { bubbles: true }));
				input.dispatchEvent(new Event('change', { bubbles: true }));
				""");
		this.driver.findElement(By.cssSelector("#taskForm button[type='submit']")).click();
		waitForTaskCount("1 task");

		assertThat(this.driver.findElement(By.id("taskList")).getText()).contains("Review launch notes")
			.contains("Review launch notes and capture follow-up items.")
			.contains("2026-06-08")
			.contains("MEDIUM")
			.contains("TODO");
		assertThat(this.taskRepository.count()).isEqualTo(1);
	}

	@Test
	void taskRowsCanBeCreatedEditedMarkedDoneAndDeleted() {
		this.driver.get("http://localhost:" + this.port + "/");
		waitForTaskCount("0 tasks");

		setValue("title", "E2E Dashboard Task");
		setValue("description", "Created through the refined dashboard UI.");
		setValue("dueDate", "2026-06-10");
		setValue("priority", "HIGH");
		setValue("status", "TODO");
		this.driver.findElement(By.id("taskSubmitButton")).click();
		waitForTaskCount("1 task");
		this.wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("taskList"), "E2E Dashboard Task"));
		assertThat(this.driver.findElement(By.id("taskList")).getText()).contains("E2E Dashboard Task")
			.contains("Created through the refined dashboard UI.")
			.contains("Due 2026-06-10")
			.contains("HIGH")
			.contains("TODO");

		this.wait.until(ExpectedConditions.elementToBeClickable(
				By.xpath("//*[@id='taskList']//button[normalize-space()='Edit']")))
			.click();
		this.wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("createTitle"), "EDIT TASK"));
		assertThat(value("title")).isEqualTo("E2E Dashboard Task");
		assertThat(value("description")).isEqualTo("Created through the refined dashboard UI.");
		assertThat(value("dueDate")).isEqualTo("2026-06-10");

		setValue("title", "E2E Dashboard Task Updated");
		setValue("description", "Updated without leaving the task list.");
		setValue("dueDate", "2026-06-12");
		setValue("priority", "MEDIUM");
		setValue("status", "IN_PROGRESS");
		this.driver.findElement(By.id("taskSubmitButton")).click();
		this.wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("formMessage"), "Task updated."));
		assertThat(this.driver.findElement(By.id("taskList")).getText()).contains("E2E Dashboard Task Updated")
			.contains("Updated without leaving the task list.")
			.contains("Due 2026-06-12")
			.contains("MEDIUM")
			.contains("IN_PROGRESS");

		this.driver.findElement(By.xpath("//*[@id='taskList']//button[normalize-space()='Mark Done']")).click();
		this.wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("taskList"), "DONE"));
		assertThat(this.driver.findElement(By.id("taskList")).getText()).doesNotContain("Mark Done");

		this.driver.findElement(By.xpath("//*[@id='taskList']//button[normalize-space()='Delete']")).click();
		waitForTaskCount("0 tasks");
		this.wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("formMessage"), "Task deleted."));
		assertThat(this.taskRepository.count()).isZero();
	}

	private WebDriver startChromeOrSkip() {
		try {
			ChromeOptions options = new ChromeOptions();
			options.addArguments("--headless=new", "--disable-gpu", "--window-size=1280,900");
			return new ChromeDriver(options);
		}
		catch (WebDriverException ex) {
			Assumptions.abort("Chrome/Selenium browser smoke test skipped because Chrome could not start: "
					+ ex.getMessage());
			return null;
		}
	}

	private void waitForTaskCount(String value) {
		this.wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("taskCount"), value));
	}

	private String value(String elementId) {
		return this.driver.findElement(By.id(elementId)).getDomProperty("value");
	}

	private void setValue(String elementId, String value) {
		js("""
				const input = document.getElementById(arguments[0]);
				input.value = arguments[1];
				input.dispatchEvent(new Event('input', { bubbles: true }));
				input.dispatchEvent(new Event('change', { bubbles: true }));
				""", elementId, value);
	}

	private Object js(String script) {
		return ((JavascriptExecutor) this.driver).executeScript(script);
	}

	private Object js(String script, Object... args) {
		return ((JavascriptExecutor) this.driver).executeScript(script, args);
	}
}
