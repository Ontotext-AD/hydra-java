package de.escalon.hypermedia.spring;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import org.springframework.hateoas.server.core.LastInvocationAware;
import org.springframework.hateoas.server.core.MethodInvocation;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class OntoDefaultMethodInvocation implements MethodInvocation, LastInvocationAware {
	private final Class<?> type;
	private final Method method;
	private final Object[] arguments;

	/**
	 * Creates a new {@link OntoDefaultMethodInvocation} for the given type, method and parameters.
	 *
	 * @param type must not be {@literal null}.
	 * @param method must not be {@literal null}.
	 * @param arguments must not be {@literal null}.
	 */
	public OntoDefaultMethodInvocation(Class<?> type, Method method, Object[] arguments) {

		Assert.notNull(type, "targetType must not be null!");
		Assert.notNull(method, "method must not be null!");
		Assert.notNull(arguments, "arguments must not be null!");

		this.type = type;
		this.method = method;
		this.arguments = arguments;
	}

	public OntoDefaultMethodInvocation(Method method, Object[] arguments) {
		this(method.getDeclaringClass(), method, arguments);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.hateoas.server.core.MethodInvocation#getTargetType()
	 */
	@NonNull
	public Class<?> getTargetType() {
		return this.type;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.hateoas.server.core.MethodInvocation#getMethod()
	 */
	@NonNull
	public Method getMethod() {
		return this.method;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.hateoas.server.core.MethodInvocation#getArguments()
	 */
	@NonNull
	public Object[] getArguments() {
		return this.arguments;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.hateoas.server.core.LastInvocationAware#getLastInvocation()
	 */
	@NonNull
	@Override
	public MethodInvocation getLastInvocation() {
		return this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.hateoas.server.core.LastInvocationAware#getObjectParameters()
	 */
	@NonNull
	@Override
	public Iterator<Object> getObjectParameters() {
		return Collections.emptyIterator();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(@Nullable Object o) {

		if (this == o) {
			return true;
		}

		if (!(o instanceof OntoDefaultMethodInvocation)) {
			return false;
		}

		OntoDefaultMethodInvocation that = (OntoDefaultMethodInvocation) o;

		return Objects.equals(this.type, that.type) //
				&& Objects.equals(this.method, that.method) //
				&& Arrays.equals(this.arguments, that.arguments);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {

		int result = Objects.hash(this.type, this.method);
		result = 31 * result + Arrays.hashCode(this.arguments);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return "DefaultMethodInvocation(targetType=" + this.type //
				+ ", method=" + this.method //
				+ ", arguments=" + Arrays.deepToString(this.arguments) + ")";
	}
}
