package com.eulerity.taskmanager.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StaticUiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rootServesStaticTaskManagerUi() throws Exception {
		this.mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(forwardedUrl("index.html"));

		this.mockMvc.perform(get("/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<title>Eulerity Task Manager</title>")))
			.andExpect(content().string(containsString("<h1>Task Manager</h1>")))
			.andExpect(content().string(containsString("src=\"/app.js\"")));
	}
}
