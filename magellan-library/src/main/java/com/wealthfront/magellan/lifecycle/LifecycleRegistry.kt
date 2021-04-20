package com.wealthfront.magellan.lifecycle

import android.content.Context

internal class LifecycleRegistry : LifecycleAware {

  val listeners: Set<LifecycleAware>
    get() = listenersToMaxStates.keys
  var listenersToMaxStates: Map<LifecycleAware, LifecycleLimit> = linkedMapOf()
    private set
  private val lifecycleStateMachine = LifecycleStateMachine()

  internal var currentState: LifecycleState = LifecycleState.Destroyed
    private set(newState) {
      val oldState = field
      field = newState
      listenersToMaxStates.forEach { (lifecycleAware, maxState) ->
        if (newState.isWithinLimit(maxState)) {
          lifecycleStateMachine.transition(lifecycleAware, oldState, newState)
        }
      }
    }

  fun attachToLifecycle(
    lifecycleAware: LifecycleAware,
    detachedState: LifecycleState = LifecycleState.Destroyed,
    maxState: LifecycleLimit = LifecycleLimit.NO_LIMIT
  ) {
    lifecycleStateMachine.transition(lifecycleAware, detachedState, currentState.limitBy(maxState))
    listenersToMaxStates = listenersToMaxStates + (lifecycleAware to maxState)
  }

  fun removeFromLifecycle(
    lifecycleAware: LifecycleAware,
    detachedState: LifecycleState = LifecycleState.Destroyed
  ) {
    listenersToMaxStates = listenersToMaxStates - lifecycleAware
    lifecycleStateMachine.transition(lifecycleAware, currentState, detachedState)
  }

  fun updateMaxState(lifecycleAware: LifecycleAware, maxState: LifecycleLimit) {
    if (!listenersToMaxStates.containsKey(lifecycleAware)) {
      throw IllegalArgumentException(
        "Cannot update the state of a lifecycleAware that is not a child: " +
          lifecycleAware::class.java.simpleName
      )
    }
    val oldMaxState = listenersToMaxStates[lifecycleAware]!!
    val needsToTransition = !currentState.isWithinLimit(minOf(maxState, oldMaxState))
    if (needsToTransition) {
      lifecycleStateMachine.transition(
        lifecycleAware,
        currentState.limitBy(oldMaxState),
        currentState.limitBy(maxState)
      )
    }
    listenersToMaxStates = listenersToMaxStates + (lifecycleAware to maxState)
  }

  override fun create(context: Context) {
    currentState = LifecycleState.Created(context)
  }

  override fun show(context: Context) {
    currentState = LifecycleState.Shown(context)
  }

  override fun resume(context: Context) {
    currentState = LifecycleState.Resumed(context)
  }

  override fun pause(context: Context) {
    currentState = LifecycleState.Shown(context)
  }

  override fun hide(context: Context) {
    currentState = LifecycleState.Created(context)
  }

  override fun destroy(context: Context) {
    currentState = LifecycleState.Destroyed
  }

  override fun backPressed(): Boolean = onAllListenersUntilTrue { it.backPressed() }

  private fun onAllListenersUntilTrue(action: (LifecycleAware) -> Boolean): Boolean =
    listenersToMaxStates.keys.asSequence().map(action).any { it }
}

public enum class LifecycleLimit(internal val order: Int) {
  DESTROYED(0), CREATED(1), SHOWN(2), NO_LIMIT(3)
}

public fun LifecycleState.isWithinLimit(limit: LifecycleLimit): Boolean = order <= limit.order

public fun LifecycleState.limitBy(limit: LifecycleLimit): LifecycleState = if (isWithinLimit(limit)) {
  this
} else {
  limit.getMaxLifecycleState(context!!)
}

public fun LifecycleLimit.getMaxLifecycleState(context: Context): LifecycleState = when (this) {
  LifecycleLimit.DESTROYED -> LifecycleState.Destroyed
  LifecycleLimit.CREATED -> LifecycleState.Created(context)
  LifecycleLimit.SHOWN -> LifecycleState.Shown(context)
  LifecycleLimit.NO_LIMIT -> LifecycleState.Resumed(context)
}
