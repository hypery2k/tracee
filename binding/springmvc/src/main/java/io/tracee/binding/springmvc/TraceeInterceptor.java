package io.tracee.binding.springmvc;

import io.tracee.Tracee;
import io.tracee.TraceeBackend;
import io.tracee.TraceeConstants;
import io.tracee.Utilities;
import io.tracee.configuration.TraceeFilterConfiguration;
import io.tracee.transport.HttpHeaderTransport;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import static io.tracee.configuration.TraceeFilterConfiguration.Channel.IncomingRequest;
import static io.tracee.configuration.TraceeFilterConfiguration.Channel.IncomingResponse;
import static io.tracee.configuration.TraceeFilterConfiguration.Channel.OutgoingResponse;

public final class TraceeInterceptor implements HandlerInterceptor {

	private final TraceeBackend backend;
	private final HttpHeaderTransport httpHeaderSerialization;
	private String outgoingHeaderName = TraceeConstants.TPIC_HEADER;
	private String incomingHeaderName = TraceeConstants.TPIC_HEADER;
	private String profileName;

	public TraceeInterceptor() {
		this(Tracee.getBackend());
	}

	protected TraceeInterceptor(TraceeBackend backend) {
		this.backend = backend;
		httpHeaderSerialization = new HttpHeaderTransport();
	}

	@Override
	public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object o) {

		final TraceeFilterConfiguration configuration = backend.getConfiguration(profileName);

		if (configuration.shouldProcessContext(IncomingRequest)) {
			@SuppressWarnings("unchecked")
			final Enumeration<String> headers = request.getHeaders(incomingHeaderName);
			if (headers != null && headers.hasMoreElements()) {
				final Map<String, String> parsedContext = httpHeaderSerialization.parse(Collections.list(headers));
				backend.putAll(configuration.filterDeniedParams(parsedContext, IncomingResponse));
			}
		}

		Utilities.generateInvocationIdIfNecessary(backend);

		final HttpSession session = request.getSession(false);
		if (session != null) {
			Utilities.generateSessionIdIfNecessary(backend, session.getId());
		}

		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object o, ModelAndView modelAndView) {
		final TraceeFilterConfiguration configuration = backend.getConfiguration(profileName);

		if (!backend.isEmpty() && configuration.shouldProcessContext(OutgoingResponse)) {
			final Map<String, String> filteredContext = configuration.filterDeniedParams(backend.copyToMap(), OutgoingResponse);
			response.setHeader(outgoingHeaderName, httpHeaderSerialization.render(filteredContext));
		}
	}

	@Override
	public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) {
		backend.clear();
	}

	public void setOutgoingHeaderName(String outgoingHeaderName) {
		this.outgoingHeaderName = outgoingHeaderName;
	}

	public void setIncomingHeaderName(String incomingHeaderName) {
		this.incomingHeaderName = incomingHeaderName;
	}

	public String getProfileName() {
		return profileName;
	}

	public void setProfileName(String profileName) {
		this.profileName = profileName;
	}
}
