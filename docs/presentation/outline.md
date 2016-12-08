# Title Slide
Icosatree Data Partitioning of Massive Geospatial Point Clouds with User-Selectable Entities and Surface Modeling



# Committee Slide
Thesis Advisory Panel
- Dr. Reynold Bailey
-- Committee Chair, Associate Professor, Associate Undergraduate Program Coordinator

- Dr. Joe Geigel
-- Committee Reader, Professor

- Dr. Zack Butler
-- Committee Observer, Associate Professor, Associate Graduate Coordinator



# Author Slide
In Partial Fulfillment of the Requirements for the Degree of
**Master of Science in Computer Science**
By Stephen Ranger
December 12, 2016



# Introduction Slide
Icosatree Partition Image / Point Cloud Image
Applying Icosatree Data Structure and Adding Analysis Tools to Point Clouds



# Related Work Slide(s) (explain each?)
- Lingyun Yu, Konstantinos Efstathiou, Petra Isenberg, and Tobias Isenberg. 2016
-- CAST: Effective and Efficient User Interaction for Context-Aware Selection in 3D Particle Clouds
- Ruggero Pintus, Enrico Gobbetti, and Marco Agus. 2011
-- Real-time Rendering of Massive Unstructured Raw Point Clouds using Screen-space Operators
- K Wenzel, M Rothermel, D Fritsch, and N Haala. 2014 
-- An out-of-core octree for massive point cloud processing
- Prashant Goswami, Yanci Zhang, Renato Pajarola, and Enrico Gobbetti. 2010 
-- High quality interactive rendering of massive point models using multi-way kd-trees
- Jürgen Richter, RicoDöllner. 2010
-- Out-of-core real-time visualization of massive 3d point clouds



# Methods
## Overview Slide
- Icosatree designed to apply Cartesian values to projected surface
-- Tree cells tangential to WGS84 projection
-- Higher fill ratio compared to Octree
-- Uniform cell shape in visualization
- Selection algorithm adds analysis utility to visualization
-- 2D lasso selection for intuitive interaction
-- Configurable steps allow tailoring to use-case
-- Export functionality to support external processing

## Icosatree Slide 1
- Icosatree: Icosahedron Data Structure
-- Based on Octree algorithm
-- Root cell splits into twenty triangular prism cells
--- Labeled A-T
-- Triangular prism cells split into eight child cells
--- Labeled 0-7
-- Traversed in same manner as Octree

## Icosatree Slide 2
- Root cell defined as axis-aligned bounding box
-- Does not store point data
- Twenty triangular prism cells encompass WGS84 surface
-- Average Effective altitude range: [-1434 km, 1465 km]
--- Corners of bottom face to center of top face
-- Max radius based on Octree radius (2^23 meters)
-- Min radius = Max radius / 2.0

## Icosatree Slide 3
- Triangular Prism Point Allocation
-- Split into sub-cells
-- Triangular Coordinates used for indexing
- Tree insert
-- Find child of root containing point
-- Insert into child
-- Compute triangular coordinate index
-- If sub-cell empty: insert point
-- Else:
--- If new point closer to sub-cell center: swap point
--- Insert remaining point into child cell

## Icosatree Slide 4
- Data structure built separately from visualization
- Application stores data structure in file structure defined by cell path
- Root directory contains point attribute CSV file and cell point statistics
- Each directory contains
-- Directories for child cells
-- Data file for stored points
-- Text file listing available child cells
- Accessible from local file system or over http connection

## Visualization Application Slide
- OpenGL / Java based (JOGL)
- Geospatial Navigation
- WGS84 based Earth
- Digital Elevation Model support
- Slippy map tiles imagery support (ie. OpenStreetMap, Stamen)

## Tree Structure Render Element Slide 1
- Handles Octree and Icosatree implementations
- Initializes tree with metadata (CSV attributes and cell statistics)
- Stores cell point data in Dynamic Vertex Buffer Pool
- Rendering Operations
-- Compute cells to render (frustum culling)
-- Upload any pending cells to vertex buffer pool
-- Build bounding volume for clipping planes
-- Render points by cell

## Tree Structure Render Element Slide 2
- Determining cells to render
-- Starting with root cell, if not outside frustum check level of detail
-- If level of detail valid, mark to render
--- If no point data, skip children, don't render, and queue point request
-- If cell has children, continue with checking each child

## Selection and Triangulation Slide 1
- 2D lasso defined by user using mouse
- Closed polygon from lasso created
- Projected into scene as selection frustum
- Points within frustum are stored and highlighted

## Selection and Triangulation Slide 2
- User defined pruning stage order
-- Orthonormal
-- Obstruction
-- Altitude threshold

## Selection and Triangulation Slide 3: Orthonormal Pruning
- Orthonormal Pruning 
-- Computes global eigenvector as surface normal
-- For each point, compute local eigenvector
-- If angle between global and local normal within threshold; remove point
- Useful for removing flat surfaces
- Unfortunately removes flat building roofs
-- However, triangulation does successfully connect building edges

## Selection and Triangulation Slide 4: Obstruction Pruning
- Obstruction Pruning 
-- For each point:
--- Compute angle between it and all other points
--- If angle within threshold AND behind test point; remove point
- Useful for removing points hidden by others
- Not useful for natural surfaces
- Helps triangulation build smoother surfaces

## Selection and Triangulation Slide 5: Altitude Threshold Pruning
- Altitude Threshold Pruning
-- Compute min/max altitude of all points
-- Remove any points within threshold of minimum
- Useful for cropping noise or ground level unevenness

## Selection and Triangulation Slide 6
- Once all points are collection and (optionally) pruned
-- Convert XYZ coordinates into XY-depth along global surface plane
-- Use Delaunay triangulation algorithm to compute triangle mesh
-- Project XY-depth mesh back to XYZ coordinates
-- Send list of triangles to rendering component


# Results
## Results Slide 1: Point Cloud Dataset
- Morro Bay, San Simeon, CA
-- Geodesic Bounds: (-120.88, 35.36), (-120.84, 35.40)
-- 163,251,931 total points

## Results Slide 2: Dataset Analysis
- Dataset construction
-- Octree Building Statistics Image
-- Icosatree Building Statistics Image
-- Octree Cell Statistics Image
-- Icosatree Cell Statistics Image

## Results Slide 3: Rendering with Level of Detail Analysis
- Rendering with level of detail
-- Depth 10 Image
-- Depth 6 Image
-- Depth 3 Image

## Results Slide 4: Point Selection Output
- Point Selection Results
-- Point Selection Example Images

## Results Slide 5: Triangulation Output
- Triangulation Results
-- Triangulation Example Images


# Discussion
# Discussion: Current Status Slide 1
- Octree/Icosatree dataset creation and rendering
-- X, Y, Z, Red, Green, Blue, Altitude, Intensity attributes
- Geospatial camera navigation
-- Geodesic anchor
-- Spherical offset
- Earth scene element
-- WGS84 ellipsoid
-- Digital Elevation Model for altitude
-- Slippy Map Tiles for imagery
-- Tessellated level of detail

# Discussion: Current Status Slide 2
- Point Selection with lasso
- Point pruning
-- Orthonormal surface
-- Obstruction
-- Altitude
- Triangulation using Delaunay algorithm
- Export using PLY format


# Conclusion Slide
- Performance overall promising
-- Icosatree cells visually pleasing
-- Cell point allocation more efficient 
-- Difficult to determine optimal sub-cell sizes
--- Cells can end up too large for GPU
--- Requires more cells to be rendered
-- Triangular Prism Bounds computationally intensive compared to axis-aligned bounds
-- Point Selection fairly quick
-- Pruning and Tessellation are slow


# Future Work Slide
- Custom point attribute mappings
- Import LAS/LAZ files directly
- Icosatree cell and sub-cell size optimization
- Updated dataset creation tool (threading, in-place file caching)
- Pruning and triangulation optimizations
- OpenCL or CUDA support
- Various rendering engine fixes



# End



# Additional Information
## Geospatial Navigation Slide
- WGS84 Surface navigation
-- Geospatial anchor
-- Spherical coordinate offset
-- Left mouse intersects navigation surface (ellipsoid) to set anchor
-- Right mouse modifies elevation and azimuth offset
-- Middle mouse scroll modifies range


## WGS84 based Earth Slide
- Tessellated ellipsoid
-- level of detail based on distance of camera from geometry
-- Digital Elevation Model data used for ellipsoid elevation
-- Slippy map tiles used for imagery


## Digital Elevation Model Slide
- Digital Elevation Model
-- Creates index of all available DEM files
-- Location set with JVM flag -Ddem3.directory
-- Recursively searches directory for .hgt files
-- Stores lookup using lon/lat integer index
-- File random access for runtime value lookup  


## Slippy map tiles Slide
- Slippy map tiles
-- Support for OpenStreetMap and Stamen
--- -Dosm.server=<base_url>
--- -Dstamen.server=<base_url>
--- -Dimage.cache=<filesystem_path>
-- URL format for geospatial tile imagery
-- Quadtree based tiling system
-- <base_url>/zoom/x/y.png (or .jpg for Stamen)
-- 256px images


## Dynamic Vertex Buffer Pool Slide
- Dynamic Vertex Buffer Object Pool
-- Actually pool of pools
-- Defines max segment size and number of segments per VBO
-- Pools created for max segment size and below
--- Loops until segment pool is less than 100 points
--- ie. max = 1000, pools = {1000, 500, 250, 125, 62}
-- Pool starts with one VBO and adds/removes additional as necessary
-- Each pool stores global index so number of VBO's seamless
-- Tree Cells store pool index and segment index for rendering
-- Allows tree cells to be at least 50% space efficient

