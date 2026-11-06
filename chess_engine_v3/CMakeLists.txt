# chess_engine_v3: the C++ engine (one UCI .exe) plus its test runner.
#
#   cmake -S chess_engine_v3 -B chess_engine_v3/build
#   cmake --build chess_engine_v3/build --config Release
#   chess_engine_v3/bin/v3_tests.exe          (or: ctest --test-dir chess_engine_v3/build -C Release)
#   chess_engine_v3/bin/engine_v3.exe         (UCI engine)
#
# Requires a CPU with AVX2 + BMI2 (Intel Haswell 2013+, AMD Zen+). The sliding-piece attack tables
# use the BMI2 PEXT instruction; the engine checks the CPU at start-up and says so if it is missing.

cmake_minimum_required(VERSION 3.20)
project(chess_engine_v3 CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release)
endif()

# Every configuration writes its .exe files to chess_engine_v3/bin/ so tools have one fixed path.
foreach(cfg "" _DEBUG _RELEASE _RELWITHDEBINFO _MINSIZEREL)
  set(CMAKE_RUNTIME_OUTPUT_DIRECTORY${cfg} ${CMAKE_SOURCE_DIR}/bin)
endforeach()

if(MSVC)
  add_compile_options(/W4 /permissive- /arch:AVX2 /utf-8 /wd4146)
  add_compile_options($<$<CONFIG:Release>:/O2> $<$<CONFIG:Release>:/Oi> $<$<CONFIG:Release>:/GL>)
  add_link_options($<$<CONFIG:Release>:/LTCG>)
  # MSVC's /arch:AVX2 allows BMI2 instructions but does not define the GCC-style macro.
  add_compile_definitions(V3_BMI2=1 _CRT_SECURE_NO_WARNINGS)
else()
  add_compile_options(-Wall -Wextra -march=native -mbmi2 -mavx2)
  add_compile_options($<$<CONFIG:Release>:-O3>)
  add_compile_definitions(V3_BMI2=1)
endif()

# The default network is built into the exe (v2's best.nnue for now; v3's own nets from P5).
# Override with: cmake ... -DV3_DEFAULT_NET=path/to/net.nnue
set(V3_DEFAULT_NET "${CMAKE_SOURCE_DIR}/../chess_engine_v2/nets/best.nnue" CACHE FILEPATH
    "Network embedded in engine_v3.exe")
set(EMBEDDED_NET_CPP "${CMAKE_BINARY_DIR}/embedded_net.cpp")
add_custom_command(
  OUTPUT ${EMBEDDED_NET_CPP}
  COMMAND ${CMAKE_COMMAND} -DINPUT=${V3_DEFAULT_NET} -DOUTPUT=${EMBEDDED_NET_CPP}
          -P ${CMAKE_SOURCE_DIR}/cmake/embed.cmake
  DEPENDS ${V3_DEFAULT_NET} ${CMAKE_SOURCE_DIR}/cmake/embed.cmake
  COMMENT "Embedding network ${V3_DEFAULT_NET}")

# The engine core is a library so the engine and the tests share exactly the same code.
add_library(v3core STATIC
  src/core/bitboard.cpp
  src/board/position.cpp
  src/movegen/movegen.cpp
  src/movegen/perft.cpp
  src/nnue/nnue.cpp
  src/search/tt.cpp
  src/search/search.cpp
  src/uci/uci.cpp
  src/datagen/datagen.cpp
  ${EMBEDDED_NET_CPP}
)
target_include_directories(v3core PUBLIC src)

add_executable(engine_v3 src/main.cpp)
target_link_libraries(engine_v3 PRIVATE v3core)

add_executable(v3_tests tests/tests.cpp)
target_link_libraries(v3_tests PRIVATE v3core)

enable_testing()
add_test(NAME v3_tests COMMAND v3_tests WORKING_DIRECTORY ${CMAKE_SOURCE_DIR})
