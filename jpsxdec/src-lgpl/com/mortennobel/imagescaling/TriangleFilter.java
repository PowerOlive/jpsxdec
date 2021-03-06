/*
 * Copyright 2009, Morten Nobel-Joergensen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mortennobel.imagescaling;

/**
 * A triangle filter (also known as linear or bilinear filter).
 */
final class TriangleFilter implements ResampleFilter
{
	public double getSamplingRadius() {
		return 1.0;
	}

	public final double apply(double value)
	{
		if (value < 0.0)
		{
			value = -value;
		}
		if (value < 1.0)
		{
			return 1.0 - value;
		}
		else
		{
			return 0.0;
		}
	}

	public String getName() {
		return "Triangle";
	}
}
