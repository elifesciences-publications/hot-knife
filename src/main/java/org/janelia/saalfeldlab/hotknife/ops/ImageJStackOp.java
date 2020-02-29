/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2014 - 2017 Board of Regents of the University of
 * Wisconsin-Madison, University of Konstanz and Brian Northan.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.janelia.saalfeldlab.hotknife.ops;

import java.util.function.Consumer;

import org.janelia.saalfeldlab.hotknife.util.Util;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * A cell loader that fill {@link RandomAccessibleInterval} with data that is
 * generated by ImageJ filters.  Always goes through {@link FloatProcessor}.
 *
 * @author Stephan Saalfeld
 * @param <T> type of input and output
 */
public class ImageJStackOp<T extends RealType<T> & NativeType<T>> implements Consumer<RandomAccessibleInterval<T>> {

	final RandomAccessible<T> input;
	final Consumer<FloatProcessor> sliceFilter;
	final double minIntensity, maxIntensity;
	final int padding;

	public ImageJStackOp(
			final RandomAccessible<T> input,
			final Consumer<FloatProcessor> sliceFilter,
			final int padding,
			final double minIntensity,
			final double maxIntensity) {

		this.input = input;
		this.sliceFilter = sliceFilter;
		this.minIntensity = minIntensity;
		this.maxIntensity = maxIntensity;
		this.padding = padding;
	}

	public ImageJStackOp(
			final RandomAccessible<T> input,
			final Consumer<FloatProcessor> sliceFilter,
			final int padding) {

		this(
				input,
				sliceFilter,
				padding,
				-Float.MAX_VALUE,
				Float.MAX_VALUE);
	}

	@Override
	public void accept(final RandomAccessibleInterval<T> output) {

		final T type = net.imglib2.util.Util.getTypeFromInterval(output).createVariable();
		final int n = output.numDimensions();
		final long[] min = Intervals.minAsLongArray(output);
		final long[] max = Intervals.maxAsLongArray(output);

		min[0] -= padding;
		min[1] -= padding;
		max[0] += padding;
		max[1] += padding;

		final IntervalView<FloatType> inputInterval = Views.interval(
				Converters.convert(
						input,
						(a, b) -> {
							b.set(a.getRealFloat());
						},
						new FloatType()),
				min,
				max);

		RandomAccessibleInterval<FloatType> inputSlice = inputInterval;
		RandomAccessibleInterval<T> outputSlice = output;
		final long[] slicePosition = min.clone();

		for (int d = 2; d < n;) {

			/* slice */
			for (int i = n - 1; i >= 2; --i) {
				inputSlice = Views.hyperSlice(inputInterval, i, slicePosition[i]);
				outputSlice = Views.hyperSlice(output, i, slicePosition[i]);
			}

			/* make FloatProcessor copy */
			final FloatProcessor fp = Util.materialize(inputSlice);
			fp.setMinAndMax(minIntensity, maxIntensity);
			final ImagePlus imp = new ImagePlus("", fp);

			/* do the work */
			sliceFilter.accept(fp);

			/* copy convert back to output block */
			Util.copy(
					Views.translate(
							Converters.convert(
									(RandomAccessibleInterval<T>)ImagePlusImgs.from(imp),
									(a, b) -> {
										b.setReal(Math.min(maxIntensity, Math.max(minIntensity, a.getRealFloat())));
									},
									type),
							min),
					outputSlice);

			/* increase slicePositions */
			for (d = 2; d < n; ++d) {
				++slicePosition[d];
				if (slicePosition[d] <= max[d])
					break;
				else
					slicePosition[d] = min[d];
			}
		}
	}
}

