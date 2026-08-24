package com.ypkim.pinbabel.influenceranalysis.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(annotations = Controller.class)
@Profile("web")
@PrimaryAdapter
public class SsrRequestExceptionHandler {

	@ExceptionHandler(SsrRequestException.class)
	public ModelAndView handle(SsrRequestException exception, HttpServletRequest request, Model model) {
		model.addAttribute("errorStatus", exception.status().value());
		model.addAttribute("errorTitle", exception.status().is4xxClientError() ? "요청을 확인해 주세요" : "처리할 수 없습니다");
		model.addAttribute("errorMessage", exception.getMessage());
		model.addAttribute("errorReference", exception.internalCode().value());
		var fragment = "true".equalsIgnoreCase(request.getHeader("HX-Request"));
		var view = fragment
			? "influenceranalysis/fragments/error-panel :: errorPanel"
			: "influenceranalysis/error";
		var modelAndView = new ModelAndView(view, model.asMap());
		modelAndView.setStatus(exception.status());
		return modelAndView;
	}
}
