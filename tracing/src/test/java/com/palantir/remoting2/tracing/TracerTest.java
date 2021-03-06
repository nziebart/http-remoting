/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting2.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public final class TracerTest {

    @Mock
    private SpanObserver observer1;
    @Mock
    private SpanObserver observer2;
    @Mock
    private TraceSampler sampler;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void after() {
        Tracer.initTrace(Optional.of(true), Tracers.randomId());
        Tracer.setSampler(AlwaysSampler.INSTANCE);
        Tracer.unsubscribe("1");
        Tracer.unsubscribe("2");
    }

    @Test
    public void testIdsMustBeNonNullAndNotEmpty() throws Exception {
        try {
            Tracer.initTrace(Optional.empty(), null);
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("traceId must be non-empty: null");
        }

        try {
            Tracer.initTrace(Optional.empty(), "");
            fail("Didn't throw");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("traceId must be non-empty: ");
        }

        try {
            Tracer.startSpan("op", null, null);
            fail("Didn't throw");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("parentTraceId must be non-empty: null");
        }

        try {
            Tracer.startSpan("op", "", null);
            fail("Didn't throw");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("parentTraceId must be non-empty: ");
        }
    }

    @Test
    public void testSubscribeUnsubscribe() throws Exception {
        // no error when completing span without a registered subscriber
        startAndCompleteSpan();

        Tracer.subscribe("1", observer1);
        Tracer.subscribe("2", observer2);
        Span span = startAndCompleteSpan();
        verify(observer1).consume(span);
        verify(observer2).consume(span);
        verifyNoMoreInteractions(observer1, observer2);

        assertThat(Tracer.unsubscribe("1")).isEqualTo(observer1);
        span = startAndCompleteSpan();
        verify(observer2).consume(span);
        verifyNoMoreInteractions(observer1, observer2);

        assertThat(Tracer.unsubscribe("2")).isEqualTo(observer2);
        startAndCompleteSpan();
        verifyNoMoreInteractions(observer1, observer2);
    }

    @Test
    public void testCanSubscribeWithDuplicatesNames() throws Exception {
        Tracer.subscribe("1", observer1);
        assertThat(Tracer.subscribe("1", observer1)).isEqualTo(observer1);
        assertThat(Tracer.subscribe("1", observer2)).isEqualTo(observer1);
        assertThat(Tracer.subscribe("2", observer1)).isNull();
    }

    @Test
    public void testDoesNotNotifyObserversWhenCompletingNonexistingSpan() throws Exception {
        Tracer.subscribe("1", observer1);
        Tracer.subscribe("2", observer2);
        Tracer.completeSpan(); // no active span.
        verifyNoMoreInteractions(observer1, observer2);
    }

    @Test
    public void testObserversAreInvokedOnObservableTracesOnly() throws Exception {
        Tracer.subscribe("1", observer1);

        Tracer.initTrace(Optional.of(true), Tracers.randomId());
        Span span = startAndCompleteSpan();
        verify(observer1).consume(span);
        span = startAndCompleteSpan();
        verify(observer1).consume(span);
        verifyNoMoreInteractions(observer1);

        Tracer.initTrace(Optional.of(false), Tracers.randomId());
        startAndCompleteSpan(); // not sampled, see above
        verifyNoMoreInteractions(observer1);
    }

    @Test
    public void testDerivesNewSpansWhenTraceIsNotObservable() throws Exception {
        Tracer.initTrace(Optional.of(false), Tracers.randomId());
        Tracer.startSpan("foo");
        Tracer.startSpan("bar");
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("bar");
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("foo");
    }

    @Test
    public void testInitTraceCallsSampler() throws Exception {
        Tracer.setSampler(sampler);
        when(sampler.sample()).thenReturn(true, false);
        Tracer.subscribe("1", observer1);

        Tracer.initTrace(Optional.empty(), Tracers.randomId());
        verify(sampler).sample();
        Span span = startAndCompleteSpan();
        verify(observer1).consume(span);
        verifyNoMoreInteractions(observer1, sampler);

        Mockito.reset(observer1, sampler);
        Tracer.initTrace(Optional.empty(), Tracers.randomId());
        verify(sampler).sample();
        startAndCompleteSpan(); // not sampled, see above
        verifyNoMoreInteractions(observer1, sampler);
    }

    @Test
    public void testTraceCopyIsIndependent() throws Exception {
        Trace trace = Tracer.copyTrace();
        trace.push(mock(OpenSpan.class));
        assertThat(Tracer.completeSpan().isPresent()).isFalse();
    }

    @Test
    public void testSetTraceSetsCurrentTrace() throws Exception {
        Tracer.startSpan("operation");
        Tracer.setTrace(new Trace(true, "newTraceId"));
        assertThat(Tracer.getTraceId()).isEqualTo("newTraceId");
        assertThat(Tracer.completeSpan().isPresent()).isFalse();
    }

    @Test
    public void testCompletedSpanHasCorrectSpanType() throws Exception {
        for (SpanType type : SpanType.values()) {
            Tracer.startSpan("1", type);
            assertThat(Tracer.completeSpan().get().type()).isEqualTo(type);
        }

        // Default is LOCAL
        Tracer.startSpan("1");
        assertThat(Tracer.completeSpan().get().type()).isEqualTo(SpanType.LOCAL);
    }

    private static Span startAndCompleteSpan() {
        Tracer.startSpan("operation");
        return Tracer.completeSpan().get();
    }
}
