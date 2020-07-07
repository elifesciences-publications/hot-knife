package org.janelia.saalfeldlab.hotknife;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class ModifyAlignment
{
	public static <T extends RealType<T>> void modifyPositionField(
			final RandomAccessibleInterval<T> positionFieldCopy,
			final int[] loc,
			final double[] moveBy,
			final double[] sigma )
	{
		double[] halfKernelX = Gauss3.halfkernel( sigma[ 0 ], Gauss3.halfkernelsizes( new double[] { sigma[ 0 ] } )[ 0 ] ,false );
		double[] halfKernelY = Gauss3.halfkernel( sigma[ 1 ], Gauss3.halfkernelsizes( new double[] { sigma[ 1 ] } )[ 0 ] ,false );

		
		final Cursor< T > c = Views.iterable( positionFieldCopy ).localizingCursor();

		while ( c.hasNext() )
		{
			final T t = c.next();

			final int distX = Math.abs( c.getIntPosition( 0 ) - loc[0] );
			final int distY = Math.abs( c.getIntPosition( 1 ) - loc[1] );

			final double w;

			if ( distX < halfKernelX.length && distY < halfKernelY.length )
			{
				w = halfKernelX[ distX ] * halfKernelY[ distY ];
	
				if ( c.getIntPosition( 2 ) == 1 ) // y
					t.setReal( t.getRealDouble() + moveBy[ 1 ] * w );
				else // x
					t.setReal( t.getRealDouble() + moveBy[ 0 ] * w );
			}
		}
	}

}